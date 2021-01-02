package com.ms_square.etsyblur

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Blur.java
 *
 * @author Manabu-GT on 3/22/17.
 */
class Blur(context: Context, blurConfig: BlurConfig) : BlurEngine {

    private var blurEngine: BlurEngine? = null
    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean): Bitmap? {
        return blurEngine!!.execute(inBitmap, canReuseInBitmap)
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap): Bitmap? {
        return blurEngine!!.execute(inBitmap, outBitmap)
    }

    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean, callback: BlurEngine.Callback) {
        blurEngine!!.execute(inBitmap, canReuseInBitmap, callback)
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap, callback: BlurEngine.Callback) {
        blurEngine!!.execute(inBitmap, outBitmap, callback)
    }

    override fun destroy() {
        blurEngine!!.destroy()
    }

    override fun methodDescription(): String {
        return blurEngine!!.methodDescription()
    }

    companion object {
        private val TAG = Blur::class.java.simpleName
    }

    init {
        blurEngine = if (RenderScriptBlur.isAvailable(context)) {
            RenderScriptBlur(context, blurConfig)
        } else if (blurConfig.allowFallback()) {
            JavaFastBlur(blurConfig)
        } else {
            NoBlur()
        }
        if (blurConfig.debug()) {
            Log.d(TAG, "Used Blur Method: " + blurEngine!!.methodDescription())
        }
    }
}