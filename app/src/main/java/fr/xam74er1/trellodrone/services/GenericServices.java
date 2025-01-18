package fr.xam74er1.trellodrone.services;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.Surface;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import fr.xam74er1.trellodrone.Model.Drone;
import fr.xam74er1.trellodrone.tellolib.camera.UDPCamera;

public class GenericServices implements Runnable{

    Mat processedFrame;

    protected final Drone telloControl;
    protected final UDPCamera udpCamera;
    protected Thread thread;
    protected boolean running;

    protected Mat mat = new Mat();

    protected GenericServiceCallBackListener listener;

    private static final String TAG = "GenericServices";

    protected int HEIGHT = UDPCamera.HEIGHT;
    protected int WIDTH = UDPCamera.WIDTH;
    protected Bitmap lastImage = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);

    public GenericServices() {
        running = false;
        this.telloControl = Drone.getInstance();
        this.udpCamera = this.telloControl.getUdpCamera();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

    }

    public synchronized Mat getProcessedFrame() {
        return processedFrame;
    }

    protected synchronized void setProcessedFrame(Mat frame) {

        this.processedFrame = frame;
        this.listener.onCallBack(this.processedFrame);
    }
    public void captureLastImage() {
        Surface surface = udpCamera.getSurface();
        if (surface == null) {
            return;
        }
        mat = new Mat();
        // Use PixelCopy to copy the content of the Surface into the Bitmap
        PixelCopy.request(surface, lastImage, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                // Successfully copied the content
                Utils.bitmapToMat(lastImage, mat);
            } else {
                Log.e(TAG,"Cannot convert to mat");
                // Handle the error
                lastImage = null;
                mat = null;
            }
        }, new Handler(Looper.getMainLooper()));
    }
    public void setListener(GenericServiceCallBackListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }
}

