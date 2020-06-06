package com.ms_square.etsyblur;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * SmartAsyncPolicy.java
 *
 * Experimental implementation.
 * You can share an instance of this class within an application depending on your usage.
 *
 * @author Manabu-GT on 3/22/17.
 */
@AnyThread
public class SmartAsyncPolicy implements AsyncPolicy {

    private static final String TAG = SmartAsyncPolicy.class.getSimpleName();

    private static final float TIME_PER_DRAW_FRAME_IN_MS = 16;

    private final boolean isDebug;

    private final int deviceScreenPixels;

    private Statistics rsStatistics;

    private Statistics nonRsStatistics;

    public SmartAsyncPolicy(@NonNull Context context) {
        this(context, false);
    }

    public SmartAsyncPolicy(@NonNull Context context, boolean isDebug) {
        WindowManager wm = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        deviceScreenPixels = dm.widthPixels * dm.heightPixels;

        rsStatistics = new Statistics();
        nonRsStatistics = new Statistics();

        this.isDebug = isDebug;
    }

    @Override
    public boolean shouldAsync(boolean isRenderScript, long computation) {
        if (isRenderScript) {
            synchronized (rsStatistics) {
                if (rsStatistics.sampleCount() <= 0) {
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                                "Statistics(RS): 0 samples. %d computations. Will guess async if > %d.", computation, deviceScreenPixels / 2));
                    }
                    return computation >= deviceScreenPixels / 2;
                } else {
                    float estimatedTimeInMs = rsStatistics.timePerComp() * computation / 1000000L;
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                                "Statistics(RS): estimates %d computation will take %.3f ms.", computation, estimatedTimeInMs));
                    }
                    return estimatedTimeInMs > TIME_PER_DRAW_FRAME_IN_MS;
                }
            }
        } else {
            synchronized (nonRsStatistics) {
                if (nonRsStatistics.sampleCount() <= 0) {
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                                "Statistics(Non-RS): 0 samples. %d computations. Will guess async if > %d.", computation, deviceScreenPixels / 8));
                    }
                    return computation >= deviceScreenPixels / 8;
                } else {
                    float estimatedTimeInMs = nonRsStatistics.timePerComp() * computation / 1000000L;
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                                "Statistics(Non-RS): estimates %d computation will take %.3f ms.", computation, estimatedTimeInMs));
                    }
                    return estimatedTimeInMs > TIME_PER_DRAW_FRAME_IN_MS;
                }
            }
        }
    }

    @Override
    public void putSampleData(boolean isRenderScript, long computation, long timeInNanos) {
        float newTimePerComp = (timeInNanos * 1f) / computation;
        if (isRenderScript) {
            if (isDebug) {
                Log.d(TAG, String.format(Locale.US,
                        "Statistics(RS): %d computations actually took %d ms.", computation, timeInNanos / 1000000L));
            }
            synchronized (rsStatistics) {
                rsStatistics.updateTimePerComp(newTimePerComp);
                rsStatistics.sampleCount(rsStatistics.sampleCount() + 1);
                if (isDebug) {
                    Log.d(TAG, String.format(Locale.US, "Statistics(RS): timerPerComp-> %.3f ns, sampleCount-> %d",
                            rsStatistics.timePerComp(), rsStatistics.sampleCount()));
                }
            }
        } else {
            if (isDebug) {
                Log.d(TAG, String.format(Locale.US,
                        "Statistics(Non-RS): %d computations actually took %d ms.", computation, timeInNanos / 1000000L));
            }
            synchronized (nonRsStatistics) {
                nonRsStatistics.updateTimePerComp(newTimePerComp);
                nonRsStatistics.sampleCount(nonRsStatistics.sampleCount() + 1);
                if (isDebug) {
                    Log.d(TAG, String.format(Locale.US, "Statistics(Non-RS): timerPerComp-> %.3f ns, sampleCount-> %d",
                            nonRsStatistics.timePerComp(), nonRsStatistics.sampleCount()));
                }
            }
        }
    }

    static class Statistics {

        private static final float DEFAULT_ALPHA = 0.15f; // used for low-pass filter

        private float timePerComp;
        private int sampleCount;

        int sampleCount() {
            return sampleCount;
        }

        void sampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
        }

        float timePerComp() {
            return timePerComp;
        }

        float updateTimePerComp(float newTimePerComp) {
            if (timePerComp > 0f) {
                timePerComp = lowPassFilter(timePerComp, newTimePerComp, DEFAULT_ALPHA);
            } else {
                timePerComp = newTimePerComp;
            }
            return timePerComp;
        }

        private static float lowPassFilter(float currentValue, float newValue, float alpha) {
            return currentValue + alpha * (newValue - currentValue);
        }
    }
}
