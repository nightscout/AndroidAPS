package app.aaps.persistentNotification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat

/**
 * Draws a glucose value as a status bar icon.
 * A struck-through value marks stale data.
 */
internal object BgNotificationIcon {

    private const val SIZE_PX = 96
    private const val STRIKE_ALPHA = 140
    private const val STRIKE_WIDTH = SIZE_PX * 0.08f

    fun create(text: String, strikeThrough: Boolean = false): IconCompat {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = SIZE_PX.toFloat()
            val width = measureText(text)
            if (width > SIZE_PX) textSize = textSize * SIZE_PX / width
            textSize = textSize.coerceAtMost(SIZE_PX * 0.8f)
        }
        val baseline = SIZE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, SIZE_PX / 2f, baseline, paint)
        if (strikeThrough) {
            val strike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                color = Color.argb(STRIKE_ALPHA, 255, 255, 255)
                strokeWidth = STRIKE_WIDTH
            }
            val y = baseline - paint.textSize * 0.35f
            canvas.drawLine(0f, y, SIZE_PX.toFloat(), y, strike)
        }
        return IconCompat.createWithBitmap(bitmap)
    }
}
