package fr.xam74er1.trellodrone;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opencv.android.OpenCVLoader;

import fr.xam74er1.trellodrone.Model.Drone;
import fr.xam74er1.trellodrone.component.JoystickView;
import fr.xam74er1.trellodrone.databinding.FragmentGlobalViewBinding;
import fr.xam74er1.trellodrone.tellolib.camera.UDPCamera;
import fr.xam74er1.trellodrone.tellolib.exception.TelloConnectionException;

public class GlobalView extends Fragment {

    private FragmentGlobalViewBinding binding;
    private final String TAG = "GlobalView";
    private Drone telloControl;

    private SurfaceView surfaceView;
    private Surface surface;
    private Handler handler;

    private JoystickView joystickRigth,joystickLeft;

    private HandlerThread connectionThread;
    private Handler connectionHandler;
    private Runnable connectionRunnable;
    private Button buttonLandFly;
    private ImageButton recordButton;

    TextView debugText;


    public GlobalView() {
        this.telloControl = Drone.getInstance();
    }

    public static GlobalView newInstance(String param1, String param2) {
        GlobalView fragment = new GlobalView();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
        }
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // Handle arguments
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGlobalViewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        surfaceView = binding.surfaceView;
        buttonLandFly = binding.testButton;
        ImageButton emergencyButton = binding.emergencyButton;
        recordButton = binding.recordButton;
         debugText = binding.debugText;
        this.joystickRigth = binding.joystickRigth;
        this.joystickLeft = binding.joystickLeft;

        this.joystickLeft.setCallBack(this.telloControl.onJoystickLeft);
        this.joystickRigth.setCallBack(this.telloControl.onJoystickRight);

        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                surface = holder.getSurface();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                surface = null;
            }
        });

        buttonLandFly.setOnClickListener(v -> {
            Log.i("Test", "start");
            testCamera();
        });

        emergencyButton.setOnClickListener(view1 -> {
            onEmergecyLanding();
        });

        recordButton.setOnClickListener(view1 -> {
            onRecord();
        });
        //startConnectionThread();
    }

    private void startConnectionThread() {
        connectionThread = new HandlerThread("ConnectionThread");
        connectionThread.start();
        connectionHandler = new Handler(connectionThread.getLooper());
        connectionRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    telloControl.iniCamera(surface,handler,getContext());
                    telloControl.connect();

                    Log.i(TAG, "Connected successfully");
                    connectionThread.quitSafely();
                } catch (TelloConnectionException e) {

                    Log.e(TAG, "Connection failed, retrying..."+e.getMessage());
                    connectionHandler.postDelayed(this, 100);
                }
            }
        };
        connectionHandler.post(connectionRunnable);
    }

    public void testCamera() {
        try {
            Log.i(TAG,"Main button "+this.telloControl.isConnected()+"  "+this.telloControl.isFlying()+"  ");
            if(this.telloControl.isConnected()) {

                if (this.telloControl.isFlying()) {
                    this.telloControl.land();
                    buttonLandFly.setText("Start");

                    ShapeDrawable shapedrawable = new ShapeDrawable();
                    shapedrawable.setShape(new RectShape());
                    shapedrawable.getPaint().setColor(Color.GREEN);
                    shapedrawable.getPaint().setStrokeWidth(10f);
                    shapedrawable.getPaint().setStyle(Paint.Style.STROKE);
                    buttonLandFly.setBackground(shapedrawable);

                } else {
                    this.telloControl.takeOff();
                    //change the text of the button on the color
                    buttonLandFly.setText("Land");

                    ShapeDrawable shapedrawable = new ShapeDrawable();
                    shapedrawable.setShape(new RectShape());
                    shapedrawable.getPaint().setColor(Color.RED);
                    shapedrawable.getPaint().setStrokeWidth(10f);
                    shapedrawable.getPaint().setStyle(Paint.Style.STROKE);
                    buttonLandFly.setBackground(shapedrawable);
                }

            }else{

                try {
                    telloControl.iniCamera(surface,handler,getContext());
                    telloControl.connect();
                    Log.i(TAG, "Connected successfully");
                    buttonLandFly.setText("Start");
                } catch (TelloConnectionException e) {

                    Log.e(TAG, "Connection failed, retrying..."+e.getMessage());
                    buttonLandFly.setText("Connect to the drone");
                }

                ;
            }


        } catch (TelloConnectionException e) {
            e.printStackTrace();
            binding.debugText.setText(" Error " + e.getMessage());
        }
    }

    public void onEmergecyLanding(){
        try{
            telloControl.connect();
        }catch (Exception e){
            Log.e(TAG,e.getMessage());
        }

        try{
            telloControl.emergency();
        }catch (Exception e){
            Log.e(TAG,e.getMessage());
        }
    }


    public void onRecord(){
      UDPCamera camera = telloControl.getUdpCamera();
      if(telloControl.isConnected()) {
          try {
              if (camera.is_isRecording()) {
                  Log.d(TAG, "stop record");
                  camera.stopRecording();
                  recordButton.setImageResource(android.R.drawable.ic_media_play);
                  Toast.makeText(getActivity(), "Record stop", Toast.LENGTH_SHORT).show();
              } else {
                  Log.d(TAG, "start record");
                  camera.startRecording();
                  recordButton.setImageResource(android.R.drawable.ic_media_pause);
                  Toast.makeText(getActivity(), "Record started", Toast.LENGTH_SHORT).show();
              }
          } catch (Exception e) {
              Log.e(TAG,"Recording error"+e.getMessage()+"  "+e+"    "+e.getCause());
              e.printStackTrace();
              debugText.setText(e.getMessage());
          }

      }else{
          Toast.makeText(getActivity(), "You need to be connected!", Toast.LENGTH_SHORT).show();
      }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(telloControl != null){
            Log.i(TAG,"On destroy");
            telloControl.onDestroy();
        }
    }
}
