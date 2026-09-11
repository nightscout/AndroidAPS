package app.aaps.ui.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import java.util.concurrent.ConcurrentHashMap

object IconBitmapRenderer {

    private data class Key(val name: String, val sizePx: Int, val tintArgb: Long)

    private val cache = ConcurrentHashMap<Key, Bitmap>()

    fun render(context: Context, vector: ImageVector, sizeDp: Int, tint: Color): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val key = Key(vector.name, sizePx, tint.value.toLong())
        return cache.getOrPut(key) { rasterize(vector, sizePx, tint) }
    }

    private fun rasterize(vector: ImageVector, sizePx: Int, tint: Color): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scaleX = sizePx / vector.viewportWidth
        val scaleY = sizePx / vector.viewportHeight
        canvas.save()
        canvas.scale(scaleX, scaleY)
        drawGroup(canvas, vector.root, tint)
        canvas.restore()
        return bitmap
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, tint: Color) {
        canvas.save()
        if (group.pivotX != 0f || group.pivotY != 0f)
            canvas.translate(group.pivotX, group.pivotY)
        if (group.rotation != 0f) canvas.rotate(group.rotation)
        if (group.scaleX != 1f || group.scaleY != 1f) canvas.scale(group.scaleX, group.scaleY)
        if (group.translationX != 0f || group.translationY != 0f)
            canvas.translate(group.translationX, group.translationY)
        if (group.pivotX != 0f || group.pivotY != 0f)
            canvas.translate(-group.pivotX, -group.pivotY)

        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, tint)
                is VectorPath  -> drawPath(canvas, node, tint)
            }
        }
        canvas.restore()
    }

    private fun drawPath(canvas: Canvas, vp: VectorPath, tint: Color) {
        val androidPath = PathParser().addPathNodes(vp.pathData).toPath().asAndroidPath()
        val tintArgb = tint.toArgb()

        if (vp.fill != null) {
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = tintArgb
                alpha = (vp.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
            }
            canvas.drawPath(androidPath, paint)
        }
        if (vp.stroke != null && vp.strokeLineWidth > 0f) {
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = tintArgb
                alpha = (vp.strokeAlpha.coerceIn(0f, 1f) * 255).toInt()
                strokeWidth = vp.strokeLineWidth
                strokeCap = vp.strokeLineCap.toAndroid()
                strokeJoin = vp.strokeLineJoin.toAndroid()
                strokeMiter = vp.strokeLineMiter
            }
            canvas.drawPath(androidPath, paint)
        }
    }

    private fun StrokeCap.toAndroid(): Paint.Cap = when (this) {
        StrokeCap.Round  -> Paint.Cap.ROUND
        StrokeCap.Square -> Paint.Cap.SQUARE
        else             -> Paint.Cap.BUTT
    }

    private fun StrokeJoin.toAndroid(): Paint.Join = when (this) {
        StrokeJoin.Round -> Paint.Join.ROUND
        StrokeJoin.Bevel -> Paint.Join.BEVEL
        else             -> Paint.Join.MITER
    }
}
