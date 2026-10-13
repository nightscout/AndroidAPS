package app.aaps.core.ui.elements

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

class VectorHitTestImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val HIT_TEST_SIZE = 64 // Width of our invisible test bitmap
    }

    // Hit test properties (completely separate from rendering)
    private var hitTestBitmap: Bitmap? = null
    private var lastDrawableHash: Int = 0
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    init {
        isClickable = true
        // Keep hardware acceleration for rendering
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
            lastWidth = w
            lastHeight = h
            maybeUpdateHitTestBitmap()
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        val newHash = drawable?.hashCode() ?: 0
        if (newHash != lastDrawableHash) {
            lastDrawableHash = newHash
            maybeUpdateHitTestBitmap()
        }
    }

    private fun maybeUpdateHitTestBitmap() {
        if (width <= 0 || height <= 0) return

        hitTestBitmap?.recycle()
        val drawable = drawable ?: return

        hitTestBitmap = when (drawable) {
            is VectorDrawable -> {
                // Create tiny bitmap for hit testing only
                val height = (HIT_TEST_SIZE * drawable.intrinsicHeight / drawable.intrinsicWidth.toFloat()).toInt()
                if (height <= 0) return

                Bitmap.createBitmap(HIT_TEST_SIZE, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                    drawable.draw(canvas)
                }
            }
            else -> null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Lazy initialization if needed
            if (hitTestBitmap == null) maybeUpdateHitTestBitmap()

            hitTestBitmap?.let { bitmap ->
                // Convert touch coordinates to bitmap space
                val x = (event.x * bitmap.width / width).toInt().coerceIn(0, bitmap.width - 1)
                val y = (event.y * bitmap.height / height).toInt().coerceIn(0, bitmap.height - 1)

                if (bitmap.getPixel(x, y) == Color.TRANSPARENT) {
                    return false
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hitTestBitmap?.recycle()
        hitTestBitmap = null
    }
}