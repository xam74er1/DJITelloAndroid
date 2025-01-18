package fr.xam74er1.trellodrone.services;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.Surface;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;

import fr.xam74er1.trellodrone.Model.Drone;
import fr.xam74er1.trellodrone.tellolib.camera.UDPCamera;

public class RoadFollowerServices extends GenericServices {


    public static RoadFollowerServices instance;

    private String TAG = "ROADFOLLOWERSERICES";


    public RoadFollowerServices() {

    }

    @Override
    public void run() {
        while (running) {
            // Simulate processing a frame
            captureLastImage();
            Mat frame = mat;
            Log.d(TAG,"Frame is"+frame);
            if (frame != null) {
                try{
                    Mat grayFrame = new Mat(frame.rows(), frame.cols(), CvType.CV_8UC1);
                    Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
                    this.setProcessedFrame(grayFrame);
                }catch (Exception e){
                    Log.e(TAG,"Error "+e);
                }

            }
            try {
                Thread.sleep(100); // Adjust the sleep time as needed
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }





    public static RoadFollowerServices getInstance() {
        if (instance == null) {
            instance = new RoadFollowerServices();
        }
        return instance;
    }
}