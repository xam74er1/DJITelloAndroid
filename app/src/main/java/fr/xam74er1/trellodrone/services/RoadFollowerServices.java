package fr.xam74er1.trellodrone.services;

import android.util.Log;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class RoadFollowerServices extends GenericServices {

    private Point pathCenter = new Point();
    private final int ROI_HEIGHT_PERCENTAGE = 60; // Bottom 60% of the image
    private final int SCAN_LINE_Y_OFFSET = 100; // Pixels from bottom to scan line

    private float angle = 0;

    private int issue_count = 0;

    private static final String TAG = "RoadFollowerServices";

    private static RoadFollowerServices instance;

    public RoadFollowerServices() {


    }

    public static RoadFollowerServices getInstance() {
        if (instance == null) {
            instance = new RoadFollowerServices();
        }
        return instance;
    }

    @Override
    public void run() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        while (running) {
            int issue_count_before = issue_count;
            captureLastImage();
            Mat frame = mat;
            if (frame != null) {
                Future<?> future = executor.submit(() -> {
                    try {
                        processFrame(frame);
                    } catch (Exception e) {
                        Log.e(TAG,"Error processing frame: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                try {
                    future.get(100, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    Log.e(TAG,"Processing frame took too long, skipping to next frame.");
                    future.cancel(true);
                    issue_count++;
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    issue_count++;
                }
            } else {
                System.out.println("Frame is null");
                issue_count++;
            }

            //if we dont have any issue we can controll the drone
            if (issue_count_before == issue_count) {
                issue_count = 0;
            }
            controll();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();

    }

    public void controll(){
        if(this.canTakeDesicion){
            float upDown = 0;
            float rightLeft = 0;
            float yaw = 0;
            float forwardBackward = 0;

            float minAngle = (float) (0.1*  Math.PI/2);

            if(issue_count!=0){
                //if we hqve issue durring 2s
                //if we have to many issue try to rotate and find a line
                if(issue_count >200){
                    yaw = 20;
                    pathCenter.x = WIDTH/2;
                    angle = 0;
                }

            }
                if(pathCenter.x < WIDTH/2 - 50){
                    rightLeft = 20;
                }else if(pathCenter.x > WIDTH/2 + 50){
                    rightLeft = -20;
                }else{
                    forwardBackward = 20;
                }

                if(angle > minAngle){
                    yaw = 20;
                }else if(angle < -1*minAngle){
                    yaw = -20;
                }


            this.telloControl.setControll(rightLeft, forwardBackward, upDown, yaw);
        }
    }

    private class Line {
        Point start;
        Point end;

        Line(Point start, Point end) {
            this.start = start;
            this.end = end;
        }

        double length() {
            return Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
        }
    }


    public Mat mergeLines(Mat lines) {
        List<Line> lineList = new ArrayList<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] vec = lines.get(i, 0);
            lineList.add(new Line(new Point(vec[0], vec[1]), new Point(vec[2], vec[3])));
        }

        List<Line> mergedLines = mergeLinesPipeline(lineList);


        Mat result = new Mat(mergedLines.size(), 1, CvType.CV_32SC4);
        for (int i = 0; i < mergedLines.size(); i++) {
            Line line = mergedLines.get(i);
            result.put(i, 0, new double[]{line.start.x, line.start.y, line.end.x, line.end.y});
        }


        return result;
    }

    private  List<Line> mergeLinesPipeline(List<Line> lines) {
        List<Line> mergedLines = new ArrayList<>();
        List<Line> horizontalLines = new ArrayList<>();
        List<Line> verticalLines = new ArrayList<>();

        for (Line line : lines) {
            double angle = Math.toDegrees(Math.atan2(line.end.y - line.start.y, line.end.x - line.start.x));
            if (Math.abs(angle) > 45 && Math.abs(angle) < 135) {
                verticalLines.add(line);
            } else {
                horizontalLines.add(line);
            }
        }

        Collections.sort(horizontalLines, Comparator.comparingDouble(line -> line.start.x));
        Collections.sort(verticalLines, Comparator.comparingDouble(line -> line.start.y));

        mergedLines.addAll(mergeLinesSegments(horizontalLines));
        mergedLines.addAll(mergeLinesSegments(verticalLines));

        return mergedLines;
    }

    private  List<Line> mergeLinesSegments(List<Line> lines) {
        List<Line> mergedLines = new ArrayList<>();
        if (lines.isEmpty()) return mergedLines;

        Line currentLine = lines.get(0);
        for (int i = 1; i < lines.size(); i++) {
            Line nextLine = lines.get(i);
            if (getDistance(currentLine, nextLine) < 30 && getAngleDifference(currentLine, nextLine) < 30) {
                currentLine = mergeTwoLines(currentLine, nextLine);
            } else {
                mergedLines.add(currentLine);
                currentLine = nextLine;
            }
        }
        mergedLines.add(currentLine);

        return mergedLines;
    }

    private Line mergeTwoLines(Line line1, Line line2) {
        List<Point> points = new ArrayList<>();
        points.add(line1.start);
        points.add(line1.end);
        points.add(line2.start);
        points.add(line2.end);

        points.sort(Comparator.comparingDouble(point -> point.x));
        return new Line(points.get(0), points.get(points.size() - 1));
    }

    private static double getDistance(Line line1, Line line2) {
        return Math.min(
                Math.min(distancePointLine(line1.start, line2), distancePointLine(line1.end, line2)),
                Math.min(distancePointLine(line2.start, line1), distancePointLine(line2.end, line1))
        );
    }

    private static double distancePointLine(Point point, Line line) {
        double A = point.x - line.start.x;
        double B = point.y - line.start.y;
        double C = line.end.x - line.start.x;
        double D = line.end.y - line.start.y;

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        double param = dot / lenSq;

        double xx, yy;

        if (param < 0 || (line.start.x == line.end.x && line.start.y == line.end.y)) {
            xx = line.start.x;
            yy = line.start.y;
        } else if (param > 1) {
            xx = line.end.x;
            yy = line.end.y;
        } else {
            xx = line.start.x + param * C;
            yy = line.start.y + param * D;
        }

        double dx = point.x - xx;
        double dy = point.y - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double getAngleDifference(Line line1, Line line2) {
        double angle1 = Math.atan2(line1.end.y - line1.start.y, line1.end.x - line1.start.x);
        double angle2 = Math.atan2(line2.end.y - line2.start.y, line2.end.x - line2.start.x);
        return Math.abs(Math.toDegrees(angle1 - angle2));
    }

    public  Mat findTwoLongestLinesWithMinDistance(Mat lines, double minDistance) {
        List<Line> lineList = new ArrayList<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] vec = lines.get(i, 0);
            lineList.add(new Line(new Point(vec[0], vec[1]), new Point(vec[2], vec[3])));
        }

        if (lineList.size() < 2) {
            //System.out.println("Not enough lines found");
            return null;
        }

        // Sort lines by length in descending order
        lineList.sort((line1, line2) -> Double.compare(line2.length(), line1.length()));

        Line longestLine = null;
        Line secondLongestLine = null;

        for (Line line : lineList) {
            if (longestLine == null) {
                longestLine = line;
            } else if (secondLongestLine == null && getDistanceBetweenLines(longestLine, line) >= minDistance) {
                secondLongestLine = line;
                break;
            }
        }

        Mat result = new Mat(2, 1, CvType.CV_32SC4);
        if (longestLine != null) {
            result.put(0, 0, new double[]{longestLine.start.x, longestLine.start.y, longestLine.end.x, longestLine.end.y});
        }else{
            System.out.println("No first line found");
        }
        if (secondLongestLine != null) {
            result.put(1, 0, new double[]{secondLongestLine.start.x, secondLongestLine.start.y, secondLongestLine.end.x, secondLongestLine.end.y});
        }else{
            System.out.println("No second line found");
        }

        return result;
    }

    private static double getDistanceBetweenLines(Line line1, Line line2) {
        return Math.min(
                Math.min(distancePointLine(line1.start, line2), distancePointLine(line1.end, line2)),
                Math.min(distancePointLine(line2.start, line1), distancePointLine(line2.end, line1))
        );
    }


    public static Point findCenterPointAndDraw(Mat lines, Mat image, int roiHeight) {
        if (lines.rows() != 2) {
            throw new IllegalArgumentException("The input Mat must contain exactly 2 lines.");
        }

        double[] line1 = lines.get(0, 0);
        double[] line2 = lines.get(1, 0);

        Point start1 = new Point(line1[0], line1[1]);
        Point end1 = new Point(line1[2], line1[3]);
        Point start2 = new Point(line2[0], line2[1]);
        Point end2 = new Point(line2[2], line2[3]);

        // Calculate the center point between the two lines
        Point centerPoint = new Point(
                (start1.x + end1.x + start2.x + end2.x) / 4,
                (start1.y + end1.y + start2.y + end2.y) / 4
        );

        //point to draw with the roiHeight offset
        Point centerPointWithOffset = new Point(centerPoint.x, centerPoint.y + roiHeight);

        // Draw the center point on the image
        Imgproc.circle(image, centerPointWithOffset, 5, new Scalar(0, 255, 0), -1);

        return centerPoint;
    }

    private float calculeAngleLine(Mat lines) {
     //return the abrage angle from the line comparte to the image if the line are vertivcal the angle is 0 if they are horizontal the angle is pi/2
        double[] line1 = lines.get(0, 0);
        double[] line2 = lines.get(1, 0);

        Point start1 = new Point(line1[0], line1[1]);
        Point end1 = new Point(line1[2], line1[3]);
        Point start2 = new Point(line2[0], line2[1]);
        Point end2 = new Point(line2[2], line2[3]);

        double angle1 = Math.atan2(end1.y - start1.y, end1.x - start1.x);
        double angle2 = Math.atan2(end2.y - start2.y, end2.x - start2.x);

        return (float) ((angle1 + angle2) / 2);
    }

    private void processFrame(Mat frame) {
        Mat resized = new Mat();
        Imgproc.resize(frame, resized, new Size(800, 600));

        // Define the ROI to focus on the center of the frame
        int roiHeight = resized.rows() / 2;
        Rect roi = new Rect(0, roiHeight , resized.cols(), roiHeight);
        Mat roiMat = new Mat(resized, roi);

        // Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(roiMat, gray, Imgproc.COLOR_BGR2GRAY);

        // Apply GaussianBlur to reduce noise
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);

        // Apply Canny edge detection
        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, 50, 150);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
        Mat dilated = new Mat();
        Imgproc.dilate(edges, dilated, kernel);


        Mat cdst = new Mat();
        Imgproc.cvtColor(dilated, cdst, Imgproc.COLOR_GRAY2BGR);
        Mat cdstP = cdst.clone();

        Mat linesP = new Mat(); // will hold the results of the detection
        Mat linesFull = new Mat(); // will hold the results of the detection
        Imgproc.HoughLinesP(dilated, linesFull, 5, Math.PI/180, 400, (double) roiHeight /2, 30); // runs the actual detection

        linesP = mergeLines(linesFull);

        linesP = findTwoLongestLinesWithMinDistance(linesP, 50);

        Scalar lineColor = new Scalar(0, 255, 0);
        if(linesP == null){
            linesP = linesFull;
            lineColor = new Scalar(0, 0, 255);
        }

        for (int x = 0; x < linesP.rows(); x++) {
            double[] l = linesP.get(x, 0);
            Imgproc.line(resized, new Point(l[0], l[1] + roiHeight), new Point(l[2], l[3] + roiHeight), lineColor, 3, Imgproc.LINE_AA, 0);
        }
        if (linesP.rows() != 2) {
            this.setProcessedFrame(resized);
            return;
        }

        pathCenter = findCenterPointAndDraw(linesP, resized, roiHeight);
        angle = calculeAngleLine(linesP);

        this.setProcessedFrame(resized);
    }



}