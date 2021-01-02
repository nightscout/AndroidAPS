package com.ms_square.etsyblur

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.AnyThread
import com.ms_square.etsyblur.SmartAsyncPolicy
import java.util.*

/**
 * SmartAsyncPolicy.java
 *
 * Experimental implementation.
 * You can share an instance of this class within an application depending on your usage.
 *
 * @author Manabu-GT on 3/22/17.
 */
@AnyThread
class SmartAsyncPolicy @JvmOverloads constructor(context: Context, isDebug: Boolean = false) : AsyncPolicy {

    private val isDebug: Boolean
    private val deviceScreenPixels: Int
    private val rsStatistics: Statistics
    private val nonRsStatistics: Statistics
    override fun shouldAsync(isRenderScript: Boolean, computation: Long): Boolean {
        if (isRenderScript) {
            synchronized(rsStatistics) {
                return if (rsStatistics.sampleCount() <= 0) {
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                            "Statistics(RS): 0 samples. %d computations. Will guess async if > %d.", computation, deviceScreenPixels / 2))
                    }
                    computation >= deviceScreenPixels / 2
                } else {
                    val estimatedTimeInMs = rsStatistics.timePerComp() * computation / 1000000L
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                            "Statistics(RS): estimates %d computation will take %.3f ms.", computation, estimatedTimeInMs))
                    }
                    estimatedTimeInMs > TIME_PER_DRAW_FRAME_IN_MS
                }
            }
        } else {
            synchronized(nonRsStatistics) {
                return if (nonRsStatistics.sampleCount() <= 0) {
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                            "Statistics(Non-RS): 0 samples. %d computations. Will guess async if > %d.", computation, deviceScreenPixels / 8))
                    }
                    computation >= deviceScreenPixels / 8
                } else {
                    val estimatedTimeInMs = nonRsStatistics.timePerComp() * computation / 1000000L
                    if (isDebug) {
                        Log.d(TAG, String.format(Locale.US,
                            "Statistics(Non-RS): estimates %d computation will take %.3f ms.", computation, estimatedTimeInMs))
                    }
                    estimatedTimeInMs > TIME_PER_DRAW_FRAME_IN_MS
                }
            }
        }
    }

    override fun putSampleData(isRenderScript: Boolean, computation: Long, timeInNanos: Long) {
        val newTimePerComp = timeInNanos * 1f / computation
        if (isRenderScript) {
            if (isDebug) {
                Log.d(TAG, String.format(Locale.US,
                    "Statistics(RS): %d computations actually took %d ms.", computation, timeInNanos / 1000000L))
            }
            synchronized(rsStatistics) {
                rsStatistics.updateTimePerComp(newTimePerComp)
                rsStatistics.sampleCount(rsStatistics.sampleCount() + 1)
                if (isDebug) {
                    Log.d(TAG, String.format(Locale.US, "Statistics(RS): timerPerComp-> %.3f ns, sampleCount-> %d",
                        rsStatistics.timePerComp(), rsStatistics.sampleCount()))
                }
            }
        } else {
            if (isDebug) {
                Log.d(TAG, String.format(Locale.US,
                    "Statistics(Non-RS): %d computations actually took %d ms.", computation, timeInNanos / 1000000L))
            }
            synchronized(nonRsStatistics) {
                nonRsStatistics.updateTimePerComp(newTimePerComp)
                nonRsStatistics.sampleCount(nonRsStatistics.sampleCount() + 1)
                if (isDebug) {
                    Log.d(TAG, String.format(Locale.US, "Statistics(Non-RS): timerPerComp-> %.3f ns, sampleCount-> %d",
                        nonRsStatistics.timePerComp(), nonRsStatistics.sampleCount()))
                }
            }
        }
    }

    internal class Statistics {
        private var timePerComp = 0f
        private var sampleCount = 0
        fun sampleCount(): Int {
            return sampleCount
        }

        fun sampleCount(sampleCount: Int) {
            this.sampleCount = sampleCount
        }

        fun timePerComp(): Float {
            return timePerComp
        }

        fun updateTimePerComp(newTimePerComp: Float): Float {
            timePerComp = if (timePerComp > 0f) {
                lowPassFilter(timePerComp, newTimePerComp, DEFAULT_ALPHA)
            } else {
                newTimePerComp
            }
            return timePerComp
        }

        companion object {
            private const val DEFAULT_ALPHA = 0.15f // used for low-pass filter
            private fun lowPassFilter(currentValue: Float, newValue: Float, alpha: Float): Float {
                return currentValue + alpha * (newValue - currentValue)
            }
        }
    }

    companion object {
        private val TAG = SmartAsyncPolicy::class.java.simpleName
        private const val TIME_PER_DRAW_FRAME_IN_MS = 16f
    }

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getMetrics(dm)
        deviceScreenPixels = dm.widthPixels * dm.heightPixels
        rsStatistics = Statistics()
        nonRsStatistics = Statistics()
        this.isDebug = isDebug
    }
}