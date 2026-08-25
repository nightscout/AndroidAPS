package app.aaps.plugins.main.general.persistentNotification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BgIconRenderer @Inject constructor() {

    private companion object {

        const val SIZE_PX = 96
        const val PADDING_PX = 8
        const val MIN_TEXT_SIZE_PX = 8f
    }

    private var cachedText: String? = null
    private var cachedIcon: IconCompat? = null

    @Synchronized
    fun render(text: String): IconCompat {
        cachedIcon?.let { if (text == cachedText) return it }
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        paint.textSize = fitTextSize(paint, text, bounds)
        val x = SIZE_PX / 2f
        val y = SIZE_PX / 2f - bounds.exactCenterY()
        canvas.drawText(text, x, y, paint)
        val icon = IconCompat.createWithBitmap(bitmap)
        cachedText = text
        cachedIcon = icon
        return icon
    }

    private fun fitTextSize(paint: Paint, text: String, bounds: Rect): Float {
        val maxSide = SIZE_PX - 2 * PADDING_PX
        var size = SIZE_PX.toFloat()
        while (size > MIN_TEXT_SIZE_PX) {
            paint.textSize = size
            paint.getTextBounds(text, 0, text.length, bounds)
            if (bounds.width() <= maxSide && bounds.height() <= maxSide) break
            size -= 2f
        }
        return size
    }
}
