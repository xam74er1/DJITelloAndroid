package fr.xam74er1.trellodrone.tellolib.camera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import fr.xam74er1.trellodrone.tellolib.exception.TelloRecordException;

public class UDPCamera {

    private static final String TAG = "UDPCamera";

    private MediaCodec codec;
    private Surface surface;
    private Handler handler;
    private boolean isRunning = false;

    private static final String TELLO_IP = "192.168.10.1";
    private static final int TELLO_COMMAND_PORT = 8889;
    private static final int TELLO_VIDEO_PORT = 11111;

    private static final int WIDTH = 960;
    private static final int HEIGHT = 720;

    public static final String DEFAULT_RECORDING_PATH = "DJITrelloVideo";

    private ByteBuffer frameBuffer;
    private boolean isFrameReady = false;

    private static int MAX_PACKET_SIZE = 1460;

    private static UDPCamera instance = null;

    // Define the frame as a class member
    private Mat frame;

    // ExecutorService for background tasks
    private ExecutorService executorService;

    // MediaMuxer for video saving
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex;
    private boolean _isRecording = false;
    private MediaCodec.BufferInfo bufferInfo;

    // BlockingQueue for frame processing
    private BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>();
    private Thread processingThread;

    private Context context;

    private ByteBuffer sps, pps;
    private int frameCnt = 0;

    private ExecutorService muxFrameExecutorService = Executors.newSingleThreadExecutor();


    public UDPCamera(Surface surface, Handler handler, Context context) {
        this.surface = surface;
        this.handler = handler;
        initializeCodec();
        if (instance == null) {
            instance = this;
        }
        frame = new Mat(); // Initialize the frame
        executorService = Executors.newSingleThreadExecutor(); // Initialize the executor service
        bufferInfo = new MediaCodec.BufferInfo();
        this.context = context;
    }

    public static UDPCamera getInstance() {
        return instance;
    }

    private void initializeCodec() {
        try {
            codec = MediaCodec.createDecoderByType("video/avc");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30); // Adjust as needed
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            codec.configure(format, surface, null, 0);
            codec.start();

            frameBuffer = ByteBuffer.allocate(32 * 1024 * 1024); // 32MB buffer
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startVideoStream() {
        this.isRunning = true;
        startProcessingThread();
        new Thread(() -> {
            try (DatagramSocket videoSocket = new DatagramSocket(TELLO_VIDEO_PORT)) {
                byte[] packetBuffer = new byte[2048];
                DatagramPacket packet;
                while (isRunning) {
                    packet = new DatagramPacket(packetBuffer, packetBuffer.length);
                    videoSocket.receive(packet);
                    onReceivePacket(packet.getData(), packet.getLength());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stopVideoStream() {
        this.isRunning = false;
        stopProcessingThread();
    }

    private void startProcessingThread() {
        processingThread = new Thread(() -> {
            while (isRunning) {
                try {
                    byte[] frame = frameQueue.take(); // Wait for a frame
                    processFrame(frame, frame.length); // Process the frame
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        processingThread.start();
    }

    private void stopProcessingThread() {
        isRunning = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
    }

    private void onReceivePacket(byte[] data, int length) {
        if (length > 0) {
            frameBuffer.put(data, 0, length);
            //The pqcketeg is MAX PACKET SIZE unlss it the end of the frame
            if (length < MAX_PACKET_SIZE) {
                if (_isRecording) {
                    ByteBuffer bufferCopy = frameBuffer.duplicate();
                    muxFrameExecutorService.submit(() -> muxFrame(bufferCopy));
                }


                frameBuffer.flip();

                byte[] newBuffer = new byte[frameBuffer.limit()];
                frameBuffer.get(newBuffer);

                frameBuffer.clear();

                if (sps == null || pps == null) {
                    extractSPSPPS(newBuffer);
                }

                frameQueue.offer(newBuffer); // Enqueue the frame
            }
        }
    }



    private void processFrame(byte[] data, int length) {
        int index = codec.dequeueInputBuffer(100000);
        if (index >= 0) {
            ByteBuffer inputBuffer = codec.getInputBuffer(index);
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(data, 0, length);
                codec.queueInputBuffer(index, 0, length, System.currentTimeMillis(), 0);
            }
        }

        int outIndex = codec.dequeueOutputBuffer(bufferInfo, 100000);
        while (outIndex >= 0) {
            ByteBuffer outputBuffer = codec.getOutputBuffer(outIndex);
            MediaFormat bufferFormat = codec.getOutputFormat(outIndex); // Optionally get format

            if (outputBuffer != null) {
                // Here, we process the output buffer
                // First, we'll extract the frame bytes
                byte[] frameData = new byte[bufferInfo.size];
                outputBuffer.get(frameData);

                // Convert the frame bytes to a Mat
                Mat yuvMat = new Mat(HEIGHT + HEIGHT / 2, WIDTH, CvType.CV_8UC1);
                yuvMat.put(0, 0, frameData);

                // Convert YUV to RGB (or any other desired color format)
                Imgproc.cvtColor(yuvMat, frame, Imgproc.COLOR_YUV2RGB_I420);

                //Log.d(TAG,"frame info "+frame.size());
                // Release the YUV Mat
                yuvMat.release();
                //Log.d(TAG,"Format is "+surface);
            }

            codec.releaseOutputBuffer(outIndex, true); // Don't render to surface, only processing
            outIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private void extractSPSPPS(byte[] data) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.position(0);

        int nalType = byteBuffer.get(4) & 0x1F;

        if (nalType == 7) { // SPS
            int spsStart = findNalStartCode(byteBuffer);
            int spsEnd = findNalEndCode(byteBuffer, spsStart + 4);
            sps = ByteBuffer.wrap(data, spsStart, spsEnd - spsStart);
            Log.d(TAG, "SPS extracted");
        } else if (nalType == 8) { // PPS
            int ppsStart = findNalStartCode(byteBuffer);
            int ppsEnd = findNalEndCode(byteBuffer, ppsStart + 4);
            pps = ByteBuffer.wrap(data, ppsStart, ppsEnd - ppsStart);
            Log.d(TAG, "PPS extracted");
        }
    }

    private int findNalStartCode(ByteBuffer byteBuffer) {
        for (int i = 0; i < byteBuffer.limit() - 4; i++) {
            if (byteBuffer.get(i) == 0x00 && byteBuffer.get(i + 1) == 0x00 && byteBuffer.get(i + 2) == 0x00 && byteBuffer.get(i + 3) == 0x01) {
                return i;
            }
        }
        return -1;
    }

    private int findNalEndCode(ByteBuffer byteBuffer, int start) {
        for (int i = start; i < byteBuffer.limit() - 4; i++) {
            if (byteBuffer.get(i) == 0x00 && byteBuffer.get(i + 1) == 0x00 && byteBuffer.get(i + 2) == 0x00 && byteBuffer.get(i + 3) == 0x01) {
                return i;
            }
        }
        return byteBuffer.limit();
    }

    void muxFrame(ByteBuffer buf) {
        try {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = buf.arrayOffset();
            bufferInfo.size = buf.position() - bufferInfo.offset;
            bufferInfo.flags = (buf.get(4) & 0x1f) == 5 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            bufferInfo.presentationTimeUs = computePresentationTime(frameCnt);

            Log.d(TAG, "muxFrame frame: " + frameCnt + " size: " + bufferInfo.size + " NAL: " + (buf.get(4) & 0x1f) + " Flags: " + bufferInfo.flags + " PTS: " + bufferInfo.presentationTimeUs);

            try {
                mediaMuxer.writeSampleData(videoTrackIndex, buf, bufferInfo);
                Log.d(TAG, "muxer success for track"+videoTrackIndex);
            } catch (Exception e) {
                Log.w(TAG, "muxer failed", e);
            }
            frameCnt++;
        }catch (Exception e){
            Log.e(TAG,"execption with"+e);
        }
    }

    private static long computePresentationTime(int frameIndex) {
        return 42 + frameIndex * 1000000 / 30;
    }

    public void startRecording() {
        Log.d(TAG, "startRecording");
        if (_isRecording) {
            Log.e(TAG, "Recording is already in progress");
            return;
        }
        frameCnt = 0;
        String storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString();
        File moviesDir = new File(storageDir, DEFAULT_RECORDING_PATH);

        if (!moviesDir.exists()) {
            if (!moviesDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory for video recording");
                return;
            }
        }

        String timeStamp = new SimpleDateFormat("MMdd_HHmmss", Locale.getDefault()).format(new Date());
        String videoFileName = "TelloVideo_" + timeStamp + ".mp4";

        File videoFile = new File(moviesDir, videoFileName);
        String videoFilePath = videoFile.getAbsolutePath();

        try {
            mediaMuxer = new MediaMuxer(videoFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30); // Adjust as needed
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            if (sps != null && pps != null) {
                Log.d(TAG, "Setting SPS and PPS");
                format.setByteBuffer("csd-0", sps);
                format.setByteBuffer("csd-1", pps);
            }

            videoTrackIndex = mediaMuxer.addTrack(format);
            mediaMuxer.start();
            this._isRecording = true;
            Log.d(TAG, "Started recording video to: " + videoFilePath);

            // Notify media scanner to add the recorded video to the Gallery
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, videoFileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATA, videoFilePath);
            ContentResolver resolver = context.getContentResolver();
            //resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.d(TAG,"record started");

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to start recording", e);
            throw new TelloRecordException("Failed to start recording");
        }
    }

    public void stopRecording() {
        if (!_isRecording) {
            Log.e(TAG, "No recording in progress");
            return;
        }

        try {
            _isRecording = false;
            //sleep 1s
            Thread.sleep(1000);
            mediaMuxer.stop();
            Log.d(TAG, "Stopped recording A");
            Thread.sleep(1000);
            mediaMuxer.release();
            mediaMuxer = null;

            Log.d(TAG, "Stopped recording B");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to stop recording", e);
        }
    }

    public void onDestroy() {
        isRunning = false;
        if (codec != null) {
            codec.stop();
            codec.release();
        }
        executorService.shutdown();
        if (_isRecording) {
            stopRecording();
        }
        stopProcessingThread();
    }

    public synchronized Mat getFrame() {
        return frame;
    }

    public MediaCodec getCodec() {
        return codec;
    }

    public void setCodec(MediaCodec codec) {
        this.codec = codec;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public boolean is_isRecording() {
        return _isRecording;
    }

    public void set_isRecording(boolean _isRecording) {
        this._isRecording = _isRecording;
    }
}
