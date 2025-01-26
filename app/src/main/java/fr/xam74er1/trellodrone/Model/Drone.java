package fr.xam74er1.trellodrone.Model;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import fr.xam74er1.trellodrone.component.JoystickInterface;
import fr.xam74er1.trellodrone.tellolib.camera.UDPCamera;
import fr.xam74er1.trellodrone.tellolib.control.TelloControl;
import fr.xam74er1.trellodrone.tellolib.exception.TelloConnectionException;

public class Drone extends TelloControl {

    private UDPCamera udpCamera;
    private Surface surface;
    private Handler handler;

    private static final String TAG = "DRONE_MODEL";

    private float upDown = 0;
    private float rightLeft = 0;
    private float forwardBackward = 0;
    private float yaw = 0;

    private double minPercentage = 0.2;
    private double maxSpeed = 60;
    private boolean rcControl = true;
    private int sendCmdMs = 200;

    private HandlerThread commandThread;
    private Handler commandHandler;
    private Runnable commandRunnable;

    private static class SingletonHolder {
        public static final Drone INSTANCE = new Drone();
    }

    public static Drone getInstance() {
        return Drone.SingletonHolder.INSTANCE;
    }

    public void iniCamera(Surface surfac, Handler handler, Context context) {
        this.surface = surfac;
        this.handler = handler;
        udpCamera = UDPCamera.getInstance();
        if (udpCamera == null) {
            udpCamera = new UDPCamera(surface, handler,context);
        }
    }

    public void connect() {
        try {
            super.connect();
            super.enterCommandMode();
            super.streamOn();
            super.startKeepAlive();
            super.startStatusMonitor();
            udpCamera.startVideoStream();
            startCommandThread();
        } catch (Exception e) {
            super.disconnect();
            /*
            if (udpCamera != null) {
                udpCamera.onDestroy();
            }

             */
            throw new TelloConnectionException("Error in the connection " + e);
        }
    }

    public JoystickInterface onJoystickLeft = (x, y) -> {
        //Log.i(TAG, "joysting left " + x + " " + y);
        if (Math.abs(x) > minPercentage) {
            yaw = (float) (maxSpeed * x);
        } else {
            yaw = 0;
        }
        if (Math.abs(y) > minPercentage) {
            forwardBackward = (float) (maxSpeed * y);
        } else {
            forwardBackward = 0;
        }
    };

    public JoystickInterface onJoystickRight = (x, y) -> {
        if (Math.abs(x) > minPercentage) {
            rightLeft = (float) (maxSpeed * x);
        } else {
            rightLeft = 0;
        }
        if (Math.abs(y) > minPercentage) {
            upDown = (float) (maxSpeed * y);
        } else {
            upDown = 0;
        }
    };

    public void startCommandThread() {
        if (commandThread == null) {
            commandThread = new HandlerThread("CommandThread");
            commandThread.start();
            commandHandler = new Handler(commandThread.getLooper());
            commandRunnable = new Runnable() {
                @Override
                public void run() {
                    sendCommand();
                    commandHandler.postDelayed(this, sendCmdMs);
                }
            };
            commandHandler.post(commandRunnable);
        }
    }

    public void stopCommandThread() {
        if (commandThread != null) {
            commandHandler.removeCallbacks(commandRunnable);
            commandThread.quitSafely();
            try {
                commandThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            commandThread = null;
            commandHandler = null;
        }
    }

    private boolean sendRcJostick = false;
    private void sendCommand() {
        //if it not null
        if(sendRcJostick){
            super.flyRC((int) rightLeft, (int) forwardBackward, (int) upDown, (int) yaw);
        }

        sendRcJostick = rightLeft!=0 || upDown !=0 || yaw !=0 || forwardBackward !=0;
    }

    public void onDestroy() {
        try{
            super.streamOff();
        }catch (Exception e){
            Log.e(TAG,e.getMessage());
        }
        try{
            super.stopKeepAlive();
        }catch (Exception e){
            Log.e(TAG,e.getMessage());
        }

        try{
            udpCamera.onDestroy();
        }catch (Exception e){
            Log.e(TAG,e.getMessage());
        }

        super.disconnect();
        stopCommandThread();
    }

    public boolean isRcControl() {
        return rcControl;
    }

    public void setRcControl(boolean rcControl) {
        this.rcControl = rcControl;
    }

    public UDPCamera getUdpCamera() {
        return udpCamera;
    }

    public void setUdpCamera(UDPCamera udpCamera) {
        this.udpCamera = udpCamera;
    }

    public Surface getSurface() {
        return surface;
    }

    public void setSurface(Surface surface) {
        this.surface = surface;
    }

    public void setControll(float upDown, float rightLeft, float forwardBackward, float yaw){
        this.upDown = upDown;
        this.rightLeft = rightLeft;
        this.forwardBackward = forwardBackward;
        this.yaw = yaw;
    }
}
