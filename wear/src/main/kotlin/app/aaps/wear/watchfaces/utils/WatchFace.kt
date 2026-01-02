/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 ustwo studio inc (www.ustwo.com)
 *
 * Adapted for AndroidAPS
 * Migrated to AndroidX WatchFaceService
 */

package app.aaps.wear.watchfaces.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Rect
import android.text.format.DateFormat
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.ComplicationSlot
import android.view.WindowInsets
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Abstract base class extending WatchFaceService for Android Wear watch faces.
 * Manages time updates, display modes, and rendering.
 *
 * Migrated to AndroidX WatchFace API while maintaining backward compatibility
 * with existing watchface implementations.
 */
@SuppressLint("Deprecated")
abstract class WatchFace : WatchFaceService() {

    // State accessible to subclasses
    protected var faceRect = Rect()
    protected var is24HourFormat = false
    protected val previousTime = WatchFaceTime()
    protected val latestTime = WatchFaceTime()
    protected var isAmbient = false
    protected var lowBitAmbient = false
    protected var burnInProtection = false
    protected var watchShape = WatchShape.UNKNOWN
    protected var layoutComplete = false

    protected val currentWatchMode: WatchMode
        get() = when {
            !isAmbient                        -> WatchMode.INTERACTIVE
            burnInProtection && lowBitAmbient -> WatchMode.LOW_BIT_BURN_IN
            burnInProtection                  -> WatchMode.BURN_IN
            lowBitAmbient                     -> WatchMode.LOW_BIT
            else                              -> WatchMode.AMBIENT
        }

    fun getWidth(): Int = faceRect.width()
    fun getHeight(): Int = faceRect.height()
    fun getTime(): WatchFaceTime = latestTime

    // Abstract methods for subclasses (maintain backward compatibility)

    /**
     * Get update rate for interactive mode in milliseconds.
     *
     * Determines how frequently the watchface redraws when in interactive (active) mode.
     * Faster rates provide smoother animations (e.g., second hand) but consume more battery.
     *
     * Default: 60000ms (1 minute) - updates only when minute changes
     * Common values:
     * - 1000ms: Update every second (for second hand)
     * - 60000ms: Update every minute (battery efficient)
     *
     * @return Update interval in milliseconds
     */
    protected open fun getInteractiveModeUpdateRate(): Long = 60 * 1000L

    /**
     * Called when watchface layout is established.
     *
     * Invoked once after watchface service starts, providing screen geometry info.
     * Use this to calculate layout positions, sizes, and prepare view measurements.
     *
     * @param shape Screen shape (ROUND, SQUARE, or RECTANGLE)
     * @param screenBounds Full screen dimensions as Rect
     * @param screenInsets System UI insets (chin, notches, etc.) - may be null
     */
    @Suppress("SameParameterValue")
    protected open fun onLayout(shape: WatchShape, screenBounds: Rect, screenInsets: WindowInsets?) {}

    /**
     * Called when time changes.
     *
     * Invoked when time updates according to interactive mode update rate.
     * Typically used to detect minute/hour changes and trigger data updates.
     *
     * Default behavior: No-op. Override to handle time-based updates.
     *
     * @param oldTime Previous time value
     * @param newTime Current time value (use hasMinuteChanged(), hasHourChanged())
     */
    protected open fun onTimeChanged(oldTime: WatchFaceTime, newTime: WatchFaceTime) {}

    /**
     * Render the watchface on the canvas.
     *
     * Called whenever the watchface needs to be drawn:
     * - Time updates (based on interactive mode update rate)
     * - Watch mode changes (interactive ↔ ambient)
     * - Data updates (BG, IOB, etc.)
     * - Invalidation requests
     *
     * Implementation must:
     * - Draw all watchface elements (time, date, BG, complications, charts, etc.)
     * - Adapt rendering for current watch mode (use isAmbient(), isLowBitAmbient())
     * - Use lazy initialization pattern (check ::binding.isInitialized)
     * - Be fast (<16ms for 60fps, ideally <5ms)
     *
     * Rendering guidelines:
     * - Interactive mode: Full color, anti-aliasing, animations
     * - Ambient mode: Black & white only, no anti-aliasing, simplified
     * - Low-bit ambient: Additional restrictions (no gradients)
     *
     * @param canvas Drawing canvas to render watchface on
     */
    protected abstract fun onDraw(canvas: Canvas)

    /**
     * Called when watch mode changes.
     *
     * Invoked when watch transitions between modes:
     * - INTERACTIVE: Watch is active, full color rendering
     * - AMBIENT: Watch in always-on display, power-saving mode
     * - LOW_BIT: Ambient with limited color depth (black & white only)
     * - LOW_BIT_BURN_IN: Low-bit with burn-in protection requirements
     *
     * Typical actions:
     * - Update colors (setColorDark/Bright/LowRes)
     * - Adjust second hand visibility
     * - Simplify rendering for ambient modes
     * - Trigger redraw
     *
     * @param watchMode New watch mode
     */
    protected open fun onWatchModeChanged(watchMode: WatchMode) {}

    /**
     * Called when 12/24-hour format preference changes.
     *
     * Invoked when user changes time display format in system settings.
     * Allows watchface to adapt time formatting (AM/PM vs 24-hour).
     *
     * Default behavior: No-op. Override if watchface displays time.
     *
     * @param is24HourFormat true for 24-hour format, false for 12-hour with AM/PM
     */
    protected open fun on24HourFormatChanged(is24HourFormat: Boolean) {}

    /**
     * Called when user taps the watchface.
     *
     * Handles touch events on the watchface screen.
     * Use to implement interactive elements (e.g., double-tap to open menu, tap chart to zoom).
     *
     * Common tap types (from WatchFaceService):
     * - TAP_TYPE_TOUCH: Initial touch
     * - TAP_TYPE_TAP: Quick tap (touch + release)
     * - TAP_TYPE_TOUCH_CANCEL: Touch cancelled
     *
     * Typical implementation checks tap location against UI element bounds
     * and performs actions (open activity, change chart timeframe, etc.).
     *
     * @param tapType Type of tap event
     * @param x X coordinate of tap in pixels
     * @param y Y coordinate of tap in pixels
     * @param eventTime Timestamp of tap event in milliseconds
     */
    protected open fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {}

    // Renderer instance
    private var renderer: WatchFaceRenderer? = null

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        // Create renderer that delegates to abstract methods
        renderer = WatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            wState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )

        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = renderer!!
        ).setTapListener(object : WatchFace.TapListener {
            override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                // Ignore complication taps - they're handled by the system
                if (complicationSlot != null) return

                // Call the abstract onTapCommand method with legacy parameters for backward compatibility
                // TapEvent provides x, y coordinates and Instant timestamp
                onTapCommand(tapType, tapEvent.xPos, tapEvent.yPos, tapEvent.tapTime.toEpochMilli())
            }
        })
    }

    /**
     * Inner renderer class that bridges new AndroidX API with old abstract methods
     */
    // Shared assets for renderer (no assets needed for this implementation)
    class RendererSharedAssets : Renderer.SharedAssets {

        override fun onDestroy() {}
    }

    private inner class WatchFaceRenderer(
        private val context: Context,
        surfaceHolder: SurfaceHolder,
        private val wState: WatchState,
        currentUserStyleRepository: CurrentUserStyleRepository
    ) : Renderer.CanvasRenderer2<RendererSharedAssets>(
        surfaceHolder,
        currentUserStyleRepository,
        wState,
        CanvasType.HARDWARE,
        interactiveDrawModeUpdateDelayMillis = getInteractiveModeUpdateRate(),
        clearWithBackgroundTintBeforeRenderingHighlightLayer = false
    ) {

        private val dateTimeChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateTimeAndInvalidate()
            }
        }

        init {
            // Register broadcast receiver for time changes
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            }
            context.registerReceiver(dateTimeChangedReceiver, filter)
        }

        override suspend fun createSharedAssets(): RendererSharedAssets {
            return RendererSharedAssets()
        }

        override fun render(
            canvas: Canvas,
            bounds: Rect,
            zonedDateTime: ZonedDateTime,
            sharedAssets: RendererSharedAssets
        ) {
            // Update bounds if changed
            if (faceRect.width() != bounds.width() || faceRect.height() != bounds.height()) {
                faceRect.set(bounds)

                // Approximate watch shape from bounds
                watchShape = if (bounds.width() == bounds.height()) {
                    WatchShape.CIRCLE
                } else {
                    WatchShape.SQUARE
                }

                if (!layoutComplete) {
                    // Pass null for WindowInsets as it's not available in new API
                    onLayout(watchShape, faceRect, null)
                    layoutComplete = true
                }
            }

            // Update watch state
            val newAmbient = renderParameters.drawMode == DrawMode.AMBIENT
            if (isAmbient != newAmbient) {
                isAmbient = newAmbient
                onWatchModeChanged(currentWatchMode)
            }

            lowBitAmbient = wState.hasLowBitAmbient
            burnInProtection = wState.hasBurnInProtection

            // Update time from ZonedDateTime
            updateTime(zonedDateTime)

            // Delegate to abstract onDraw method
            onDraw(canvas)
        }

        override fun renderHighlightLayer(
            canvas: Canvas,
            bounds: Rect,
            zonedDateTime: ZonedDateTime,
            sharedAssets: RendererSharedAssets
        ) {
            canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)
        }

        private fun updateTime(zonedDateTime: ZonedDateTime) {
            previousTime.set(latestTime)

            // Convert ZonedDateTime to WatchFaceTime
            latestTime.year = zonedDateTime.year
            latestTime.month = zonedDateTime.monthValue - 1 // Calendar uses 0-based months
            latestTime.monthDay = zonedDateTime.dayOfMonth
            latestTime.hour = zonedDateTime.hour
            latestTime.minute = zonedDateTime.minute
            latestTime.second = zonedDateTime.second
            latestTime.timezone = zonedDateTime.zone.id

            onTimeChanged(previousTime, latestTime)

            val is24Hour = DateFormat.is24HourFormat(context)
            if (is24Hour != is24HourFormat) {
                is24HourFormat = is24Hour
                on24HourFormatChanged(is24HourFormat)
            }
        }

        private fun updateTimeAndInvalidate() {
            // Update from system time
            previousTime.set(latestTime)
            latestTime.setToNow()
            latestTime.timezone = TimeZone.getDefault().id

            onTimeChanged(previousTime, latestTime)

            val is24Hour = DateFormat.is24HourFormat(context)
            if (is24Hour != is24HourFormat) {
                is24HourFormat = is24Hour
                on24HourFormatChanged(is24HourFormat)
            }

            invalidate()
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                context.unregisterReceiver(dateTimeChangedReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver not registered, ignore
            }
        }
    }

    // Helper method for subclasses to trigger invalidation
    fun invalidate() {
        renderer?.invalidate()
    }

    override fun getSystemService(name: String): Any? {
        // Delegate to application context for backward compatibility
        return applicationContext.getSystemService(name) ?: super.getSystemService(name)
    }
}
