package com.ms_square.etsyblur

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Debug
import android.util.Log
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * JavaFastBlur.java
 *
 * @author Manabu-GT on 3/22/17.
 */
internal class JavaFastBlur(blurConfig: BlurConfig) : BaseBlurEngine(blurConfig) {

    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean): Bitmap? {
        return fastBlur(inBitmap, blurConfig.radius(), canReuseInBitmap)
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap): Bitmap? {
        return fastBlur(inBitmap, outBitmap, blurConfig.radius())
    }

    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean, callback: BlurEngine.Callback) {
        if (shouldAsync(inBitmap.width, inBitmap.height, blurConfig.radius())) {
            asyncTasks.add(FastBlurAsyncTask(inBitmap, null, canReuseInBitmap, callback).execute(blurConfig))
        } else {
            callback.onFinished(fastBlur(inBitmap, blurConfig.radius(), canReuseInBitmap))
        }
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap, callback: BlurEngine.Callback) {
        if (shouldAsync(inBitmap.width, inBitmap.height, blurConfig.radius())) {
            asyncTasks.add(FastBlurAsyncTask(inBitmap, outBitmap, callback).execute(blurConfig))
        } else {
            callback.onFinished(fastBlur(inBitmap, outBitmap, blurConfig.radius()))
        }
    }

    override fun methodDescription(): String {
        return "Java's FastBlur implementation"
    }

    override fun calculateComputation(bmpWidth: Int, bmpHeight: Int, radius: Int): Long {
        return (bmpHeight * (2 * radius + bmpWidth) + bmpWidth * (2 * radius + bmpHeight)).toLong()
    }

    override fun shouldAsync(bmpWidth: Int, bmpHeight: Int, radius: Int): Boolean {
        return blurConfig.asyncPolicy().shouldAsync(false, calculateComputation(bmpWidth, bmpHeight, radius))
    }

    private fun fastBlur(inBitmap: Bitmap, radius: Int, canReuseInBitmap: Boolean): Bitmap? {
        val outBitmap: Bitmap = if (canReuseInBitmap) {
            inBitmap
        } else {
            inBitmap.copy(inBitmap.config, true)
        }
        return fastBlur(inBitmap, outBitmap, radius)
    }

    // Ref...http://stackoverflow.com/questions/2067955/fast-bitmap-blur-for-android-sdk
    private fun fastBlur(inBitmap: Bitmap, outBitmap: Bitmap, radius: Int): Bitmap? {

        // Stack Blur v1.0 from
        // http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
        //
        // Java Author: Mario Klingemann <mario at quasimondo.com>
        // http://incubator.quasimondo.com
        // created Feburary 29, 2004
        // Android port : Yahel Bouaziz <yahel at kayenko.com>
        // http://www.kayenko.com
        // ported april 5th, 2012

        // This is a compromise between Gaussian Blur and Box blur
        // It creates much better looking blurs than Box Blur, but is
        // 7x faster than my Gaussian Blur implementation.
        //
        // I called it Stack Blur because this describes best how this
        // filter works internally: it creates a kind of moving stack
        // of colors whilst scanning through the image. Thereby it
        // just has to add one new block of color to the right side
        // of the stack and remove the leftmost color. The remaining
        // colors on the topmost layer of the stack are either added on
        // or reduced by one, depending on if they are on the right or
        // on the left side of the stack.
        //
        // If you are using this algorithm in your code please add
        // the following line:
        //
        // Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>
        val start = Debug.threadCpuTimeNanos()
        if (radius < 1) {
            return null
        }
        val w = inBitmap.width
        val h = inBitmap.height
        val pix = IntArray(w * h)
        inBitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        yi = 0
        yw = yi
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        y = 0
        while (y < h) {
            bsum = 0
            gsum = bsum
            rsum = gsum
            boutsum = rsum
            goutsum = boutsum
            routsum = goutsum
            binsum = routsum
            ginsum = binsum
            rinsum = ginsum
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = bsum
            rsum = gsum
            boutsum = rsum
            goutsum = boutsum
            routsum = goutsum
            binsum = routsum
            ginsum = binsum
            rinsum = ginsum
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {

                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = -0x1000000 and pix[yi] or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        outBitmap.setPixels(pix, 0, w, 0, 0, w, h)
        if (start > 0) {
            val duration = Debug.threadCpuTimeNanos() - start
            blurConfig.asyncPolicy().putSampleData(false, calculateComputation(w, h, radius), duration)
            if (blurConfig.debug()) {
                Log.d(TAG, String.format(Locale.US, "fastBlur() took %d ms.", duration / 1000000L))
            }
        }
        return outBitmap
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class FastBlurAsyncTask(private val inBitmap: Bitmap, private val outBitmap: Bitmap?,
                                           private val canReuseInBitmap: Boolean, private val callback: BlurEngine.Callback) : AsyncTask<BlurConfig?, Void?, Bitmap?>() {

        constructor(inBitmap: Bitmap, outBitmap: Bitmap?,
                    callback: BlurEngine.Callback) : this(inBitmap, outBitmap, false, callback)

        override fun doInBackground(vararg params: BlurConfig?): Bitmap? {
            val config = params[0]
            if (isCancelled) {
                return null
            }
            if (blurConfig.debug()) {
                Log.d(TAG, "Running in background...")
            }
            return if (config != null) {
                if (outBitmap != null) {
                    fastBlur(inBitmap, outBitmap, config.radius())
                } else {
                    fastBlur(inBitmap, config.radius(), canReuseInBitmap)
                }
            } else
                null
        }

        override fun onCancelled(blurredBitmap: Bitmap?) {
            asyncTasks.remove(this)
        }

        override fun onPostExecute(blurredBitmap: Bitmap?) {
            callback.onFinished(blurredBitmap)
            asyncTasks.remove(this)
        }

    }

    companion object {
        private val TAG = JavaFastBlur::class.java.simpleName
    }
}