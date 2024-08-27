package fr.xam74er1.trellodrone.component;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import fr.xam74er1.trellodrone.R;

public class JoystickView extends View {
    private Paint borderPaint;
    private Paint handlePaint;
    private float handleRadius;
    private float handleCenterX;
    private float handleCenterY;
    private float squareSide;
    private int borderColor;
    private int handleColor;
    private float strokeWidth;
    private Paint.Style borderStyle;
    private JoystickInterface callBack;

    public JoystickView(Context context) {
        super(context);
        init(null);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Joystick);
            borderColor = a.getColor(R.styleable.Joystick_borderColor, Color.GRAY);
            handleColor = a.getColor(R.styleable.Joystick_handleColor, Color.BLUE);
            strokeWidth = a.getDimension(R.styleable.Joystick_strokeWidth, 5);
            int borderStyleIndex = a.getInt(R.styleable.Joystick_borderStyle, 0);
            borderStyle = (borderStyleIndex == 0) ? Paint.Style.FILL : Paint.Style.STROKE;
            handleRadius = a.getDimension(R.styleable.Joystick_handleRadius, 50);
            a.recycle();
        } else {
            borderColor = Color.GRAY;
            handleColor = Color.BLUE;
            strokeWidth = 5;
            borderStyle = Paint.Style.FILL;
            handleRadius = 50;
        }

        borderPaint = new Paint();
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(borderStyle);
        borderPaint.setStrokeWidth(strokeWidth);

        handlePaint = new Paint();
        handlePaint.setColor(handleColor);
        handlePaint.setStyle(Paint.Style.FILL);

        handleCenterX = handleRadius;
        handleCenterY = handleRadius;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        squareSide = Math.min(w, h);
        handleCenterX = squareSide / 2f;
        handleCenterY = squareSide / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = 0;
        float top = 0;
        float right = squareSide;
        float bottom = squareSide;
        float cornerRadius = 20;

        canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, borderPaint);
        canvas.drawCircle(handleCenterX, handleCenterY, handleRadius, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        float centerX = squareSide / 2f;
        float centerY = squareSide / 2f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                handleCenterX = Math.max(handleRadius, Math.min(x, squareSide - handleRadius));
                handleCenterY = Math.max(handleRadius, Math.min(y, squareSide - handleRadius));


                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                handleCenterX = centerX;
                handleCenterY = centerY;
                invalidate();
                break;
        }

        float delta = squareSide -handleRadius- centerX;
        float prortionX = (centerX - handleCenterX)/delta;
        float prortionY = (centerY - handleCenterY)/delta;
        callBack.onJoystickMove(prortionX,prortionY);

        return true;
    }

    public void setCallBack(JoystickInterface callBack){
        this.callBack = callBack;
    }

}
