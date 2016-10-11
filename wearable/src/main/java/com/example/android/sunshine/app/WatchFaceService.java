package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class WatchFaceService extends CanvasWatchFaceService {

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private TextView dateTextView;
    private TextView hoursTextView;
    private TextView minutesTextView;
    private TextView secondsTextView;
    private RelativeLayout topLayout;
    private LinearLayout bottomLayout;
    private ImageView weatherIcon;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        Calendar gc = new GregorianCalendar();
        Date time = new Date();
        boolean mAmbient;

        float mXOffset = 0;
        float mYOffset = 0;

        private int specW, specH;
        private View myLayout;
        private final Point displaySize = new Point();

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            // Load the display spec - we'll need this later for measuring myLayout
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            display.getSize(displaySize);


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                // Update time zone in case it changed while we weren't visible.
                time = new Date();
                gc.setTime(time);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();

        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            if (insets.isRound()) {
                // Inflate the layout that we're using for the watch face
                myLayout = inflater.inflate(R.layout.round_activity_main, null);
            } else {
                myLayout = inflater.inflate(R.layout.rect_activity_main, null);
            }

            dateTextView = (TextView) myLayout.findViewById(R.id.date_text_view);
            hoursTextView = (TextView) myLayout.findViewById(R.id.hours_text_view);
            minutesTextView = (TextView) myLayout.findViewById(R.id.minutes_text_view);
            secondsTextView = (TextView) myLayout.findViewById(R.id.seconds_text_view);
            topLayout = (RelativeLayout) myLayout.findViewById(R.id.top_layout);
            bottomLayout = (LinearLayout) myLayout.findViewById(R.id.bottom_layout);
            weatherIcon = (ImageView) myLayout.findViewById(R.id.weather_icon);


            // Recompute the MeasureSpec fields - these determine the actual size of the layout
            specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY);
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                if (inAmbientMode) {
                    secondsTextView.setVisibility(View.GONE);
                    topLayout.setBackgroundColor(Color.BLACK);
                    bottomLayout.setBackgroundColor(Color.BLACK);
                    weatherIcon.setVisibility(View.GONE);
                } else {
                    secondsTextView.setVisibility(View.VISIBLE);
                    topLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.primary));
                    bottomLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
                    weatherIcon.setVisibility(View.VISIBLE);
                }
                invalidate();
            }
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Get the curent time
            time = new Date();
            gc.setTime(time);
            String day = gc.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
            String hour = String.valueOf(gc.get(Calendar.HOUR_OF_DAY));
            String minute = String.valueOf(String.format("%02d", gc.get(Calendar.MINUTE)));
            String second = String.valueOf(String.format("%02d", gc.get(Calendar.SECOND)));

            dateTextView.setText(day);
            hoursTextView.setText(hour);
            minutesTextView.setText(minute);
            secondsTextView.setText(second);

            // Update the layout
            myLayout.measure(specW, specH);
            myLayout.layout(0, 0, myLayout.getMeasuredWidth(), myLayout.getMeasuredHeight());

            // Draw it to the Canvas
            canvas.translate(mXOffset, mYOffset);
            myLayout.draw(canvas);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }


        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}

