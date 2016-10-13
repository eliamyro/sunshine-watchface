package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Element;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.EventLog;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class WatchFaceService extends CanvasWatchFaceService implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, GoogleApiClient.OnConnectionFailedListener {
    private static final String LOG_TAG = "Log_WatchFace_Service";
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    private static final long NORMAL_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);
    private long interactiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

    private GoogleApiClient mGoogleApiClient;

    private TextView dateTextView;
    private TextView hoursTextView;
    private TextView minutesTextView;
    private TextView secondsTextView;
    private LinearLayout topLayout;
    private LinearLayout bottomLayout;
    private ImageView weatherIcon;
    private TextView hiTempLabelTextView;
    private TextView lowTempLabelTextView;
    private TextView hiTempTextView;
    private TextView lowTempTextView;
    private TextView humidityTextView;

    private int weatherId;
    private String highTemp;
    private String lowTemp;
    private int humidity;


    @Override
    public Engine onCreateEngine() {

        mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
        return new Engine();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer){
            if (event.getType() == DataEvent.TYPE_CHANGED){
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().equals("/weather_path")){
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    weatherId = dataMap.getInt("weather_id");
                    highTemp = dataMap.getString("hi_temp");
                    lowTemp = dataMap.getString("low_temp");
                    humidity = dataMap.getInt("humidity");

                }
            }
        }

        Log.i(LOG_TAG, weatherId + " " + highTemp + " " + lowTemp);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        boolean mRegisteredReceiver = false;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDateFormat;
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
                            long delayMs = interactiveUpdateRateMs - (timeMs % interactiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            WatchFaceService.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            WatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mCalendar = GregorianCalendar.getInstance();
            mDate = new Date();
            initFormats();

            // Load the display spec - we'll need this later for measuring myLayout
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            display.getSize(displaySize);


        }

        private void initFormats() {
            mDateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
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
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();
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
            topLayout = (LinearLayout) myLayout.findViewById(R.id.top_layout);
            bottomLayout = (LinearLayout) myLayout.findViewById(R.id.bottom_layout);
            weatherIcon = (ImageView) myLayout.findViewById(R.id.weather_icon);
            hiTempLabelTextView = (TextView)myLayout.findViewById(R.id.hi_temp_label);
            lowTempLabelTextView = (TextView)myLayout.findViewById(R.id.low_temp_label);
            hiTempTextView = (TextView)myLayout.findViewById(R.id.hi_temp_text_view);
            lowTempTextView = (TextView)myLayout.findViewById(R.id.low_temp_text_view);
            humidityTextView = (TextView)myLayout.findViewById(R.id.humidity_text_view);


            // Recompute the MeasureSpec fields - these determine the actual size of the layout
            specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY);
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            hoursTextView.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
            minutesTextView.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
            secondsTextView.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
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
                    hiTempLabelTextView.setVisibility(View.GONE);
                    lowTempLabelTextView.setVisibility(View.GONE);
                    hiTempTextView.setVisibility(View.GONE);
                    lowTempTextView.setVisibility(View.GONE);
                    humidityTextView.setVisibility(View.GONE);
                } else {
                    secondsTextView.setVisibility(View.VISIBLE);
                    topLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.primary));
                    bottomLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.green));
                    weatherIcon.setVisibility(View.VISIBLE);
                    hiTempLabelTextView.setVisibility(View.VISIBLE);
                    lowTempLabelTextView.setVisibility(View.VISIBLE);
                    hiTempTextView.setVisibility(View.VISIBLE);
                    lowTempTextView.setVisibility(View.VISIBLE);
                    humidityTextView.setVisibility(View.VISIBLE);
                }

                invalidate();
            }
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == android.support.wearable.watchface.WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            interactiveUpdateRateMs = (inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

//            // Get the curent time
            mDate = new Date();
            mCalendar.setTime(mDate);
            String date = mDateFormat.format(mDate);

            String day = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
            String hour = String.valueOf(String.format("%02d",mCalendar.get(Calendar.HOUR_OF_DAY)));
            String minute = String.valueOf(String.format("%02d", mCalendar.get(Calendar.MINUTE)));
            String second = String.valueOf(String.format("%02d", mCalendar.get(Calendar.SECOND)));

            dateTextView.setText(day + " " + date);
            hoursTextView.setText(hour);
            minutesTextView.setText(minute);
            secondsTextView.setText(second);
            lowTempTextView.setText(lowTemp);
            hiTempTextView.setText(highTemp);
            humidityTextView.setText(String.format("hum: %s",humidity+"%"));
            weatherIcon.setImageResource(getIconResourceForWeatherCondition(weatherId));

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

    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }
}

