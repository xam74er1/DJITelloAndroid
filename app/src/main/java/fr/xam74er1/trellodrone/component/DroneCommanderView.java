package fr.xam74er1.trellodrone.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class DroneCommanderView extends View {

    private Paint borderPaint;
    private Paint textPaint;
    private Drawable iconDrawable;
    private String topText = "Drone Commander";
    private String bottomDescription = "Autonomous Task Runner";
    private boolean isRunning = false;
    private Thread loopThread;

    public DroneCommanderView(Context context) {
        super(context);
        init();
    }

    public DroneCommanderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DroneCommanderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize the paint for the border
        borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5);

        // Initialize the paint for the text
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Load the icon drawable (replace with your icon)
        iconDrawable = getResources().getDrawable(android.R.drawable.ic_menu_camera, null);
        iconDrawable.setBounds(new Rect(0, 0, 100, 100));

        // Set the onClick listener for the square
        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                callback();
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw the square with a white border
        Rect squareRect = new Rect(50, 100, width - 50, height - 100);
        canvas.drawRect(squareRect, borderPaint);

        // Draw the icon in the middle of the square
        int iconLeft = (width - iconDrawable.getIntrinsicWidth()) / 2;
        int iconTop = (height - iconDrawable.getIntrinsicHeight()) / 2;
        canvas.save();
        canvas.translate(iconLeft, iconTop);
        iconDrawable.draw(canvas);
        canvas.restore();

        // Draw the text on the top of the square
        canvas.drawText(topText, width / 2, 70, textPaint);

        // Draw the description at the bottom of the square
        canvas.drawText(bottomDescription, width / 2, height - 50, textPaint);
    }

    private void callback() {
        // Implement the callback logic here
        System.out.println("Square clicked!");
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            loopThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRunning) {
                        onExecute();
                        try {
                            Thread.sleep(100); // Loop every 100 ms
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            loopThread.start();
        }
    }

    public void stop() {
        isRunning = false;
        if (loopThread != null) {
            loopThread.interrupt();
        }
    }

    private void onExecute() {
        // Implement the logic to be executed on each loop here
        System.out.println("Executing task...");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int width = getWidth();
        int height = getHeight();
        Rect squareRect = new Rect(50, 100, width - 50, height - 100);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (squareRect.contains((int) event.getX(), (int) event.getY())) {
                performClick();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        // Handle the click event here
        callback();
        return true;
    }
}
