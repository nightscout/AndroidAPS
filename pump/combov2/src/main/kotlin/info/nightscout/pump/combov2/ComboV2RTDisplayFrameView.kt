package info.nightscout.pump.combov2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import info.nightscout.comboctl.base.DISPLAY_FRAME_HEIGHT
import info.nightscout.comboctl.base.DISPLAY_FRAME_WIDTH
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.NUM_DISPLAY_FRAME_PIXELS
import info.nightscout.comboctl.base.NullDisplayFrame

/**
 * Custom [View] to show a Combo remote terminal display frame on the UI.
 *
 * The [DisplayFrame] is shown on the UI via [Canvas]. To do that, the frame
 * is converted to a [Bitmap], and then that bitmap is rendered on the UI.
 *
 * Callers pass new frames to the view by setting the [displayFrame] property.
 * The frame -> bitmap conversion happens on-demand, when [onDraw] is called.
 * That way, if this view is not shown on the UI (because for example the
 * associated fragment is not visible at the moment), no unnecessary conversions
 * are performed, saving computational effort.
 *
 * The frame is drawn unsmoothed to better mimic the Combo's LCD.
 */
internal class ComboV2RTDisplayFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private companion object {
        const val BACKGROUND_SHADE = 0xB5
        const val FOREGROUND_SHADE = 0x20
    }

    private val bitmap = Bitmap.createBitmap(DISPLAY_FRAME_WIDTH, DISPLAY_FRAME_HEIGHT, Bitmap.Config.ARGB_8888, false)
    private val bitmapPixels = IntArray(NUM_DISPLAY_FRAME_PIXELS) { BACKGROUND_SHADE }
    private val bitmapPaint = Paint().apply {
        style = Paint.Style.FILL
        // These are necessary to ensure nearest neighbor scaling.
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val bitmapRect = Rect()

    private var isNewDisplayFrame = true
    var displayFrame = NullDisplayFrame
        set(value) {
            field = value
            isNewDisplayFrame = true
            // Necessary to inform Android that during
            // the next UI update it should call onDraw().
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        updateBitmap()
        canvas.drawBitmap(bitmap, null, bitmapRect, bitmapPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmapRect.set(0, 0, w, h)
    }

    private fun updateBitmap() {
        if (!isNewDisplayFrame)
            return

        for (pixelIdx in 0 until NUM_DISPLAY_FRAME_PIXELS) {
            val srcPixel = if (displayFrame[pixelIdx]) FOREGROUND_SHADE else BACKGROUND_SHADE
            bitmapPixels[pixelIdx] = Color.argb(0xFF, srcPixel, srcPixel, srcPixel)
        }

        bitmap.setPixels(bitmapPixels, 0, DISPLAY_FRAME_WIDTH, 0, 0, DISPLAY_FRAME_WIDTH, DISPLAY_FRAME_HEIGHT)

        isNewDisplayFrame = false
    }
}