package com.ms_square.etsyblur

import android.graphics.Bitmap

/**
 * NoBlur.java
 *
 * @author Manabu-GT on 3/22/17.
 */
internal class NoBlur : BlurEngine {

    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean): Bitmap? {
        return null
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap): Bitmap? {
        return null
    }

    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean, callback: BlurEngine.Callback) {
        callback.onFinished(null)
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap, callback: BlurEngine.Callback) {
        callback.onFinished(null)
    }

    override fun destroy() {}
    override fun methodDescription(): String {
        return "No Blur Effect"
    }
}