package com.ms_square.etsyblur

import android.content.Context
import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Debug
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RSRuntimeException
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import java.util.*

/**
 * RenderScriptBlur.java
 *
 * @author Manabu-GT on 3/22/17.
 */
internal class RenderScriptBlur(context: Context, blurConfig: BlurConfig) : BaseBlurEngine(blurConfig) {

    private val LOCK = Any()
    private val rs: RenderScript?
    private val scriptBlur: ScriptIntrinsicBlur?
    private var input: Allocation? = null
    private var output: Allocation? = null
    private var width = 0
    private var height = 0
    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean): Bitmap? {
        return blur(inBitmap, if (canReuseInBitmap) inBitmap else inBitmap.copy(inBitmap.config, true))
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap): Bitmap? {
        return blur(inBitmap, outBitmap)
    }

    override fun execute(inBitmap: Bitmap, canReuseInBitmap: Boolean, callback: BlurEngine.Callback) {
        if (shouldAsync(inBitmap.width, inBitmap.height, blurConfig.radius())) {
            asyncTasks.add(BlurAsyncTask(inBitmap,
                if (canReuseInBitmap) inBitmap else inBitmap.copy(inBitmap.config, true), callback).execute())
        } else {
            callback.onFinished(execute(inBitmap, canReuseInBitmap))
        }
    }

    override fun execute(inBitmap: Bitmap, outBitmap: Bitmap, callback: BlurEngine.Callback) {
        if (shouldAsync(inBitmap.width, inBitmap.height, blurConfig.radius())) {
            asyncTasks.add(BlurAsyncTask(inBitmap, outBitmap, callback).execute())
        } else {
            callback.onFinished(blur(inBitmap, outBitmap))
        }
    }

    override fun destroy() {
        super.destroy()
        synchronized(LOCK) {

            // clean up renderscript resources
            rs?.destroy()
            scriptBlur?.destroy()
            destroyInputOutput()
        }
    }

    override fun methodDescription(): String {
        return "RenderScript's ScriptIntrinsicBlur"
    }

    override fun calculateComputation(bmpWidth: Int, bmpHeight: Int, radius: Int): Long {
        return (bmpWidth * bmpHeight).toLong()
    }

    override fun shouldAsync(bmpWidth: Int, bmpHeight: Int, radius: Int): Boolean {
        return blurConfig.asyncPolicy().shouldAsync(true, calculateComputation(bmpWidth, bmpHeight, radius))
    }

    private fun blur(inBitmap: Bitmap, outBitmap: Bitmap?): Bitmap? {
        val start = Debug.threadCpuTimeNanos()
        val newWidth = inBitmap.width
        val newHeight = inBitmap.height
        synchronized(LOCK) {
            if (input == null || width != newWidth || height != newHeight) {
                width = newWidth
                height = newHeight
                destroyInputOutput()
                input = Allocation.createFromBitmap(rs, inBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT)
                input?.type?.let { val inputType =  input!!.type
                    output = Allocation.createTyped(rs, inputType)
                }
            }
            input!!.copyFrom(inBitmap)
            scriptBlur!!.setRadius(blurConfig.radius().toFloat())
            scriptBlur.setInput(input)
            scriptBlur.forEach(output)
            output!!.copyTo(outBitmap)
        }
        if (start > 0) {
            val duration = Debug.threadCpuTimeNanos() - start
            blurConfig.asyncPolicy().putSampleData(true, calculateComputation(newWidth, newHeight, blurConfig.radius()), duration)
            if (blurConfig.debug()) {
                Log.d(TAG, String.format(Locale.US, "RenderScriptBlur took %d ms.", duration / 1000000L))
            }
        }
        return outBitmap
    }

    private fun destroyInputOutput() {
        if (input != null) {
            input!!.destroy()
            input = null
        }
        if (output != null) {
            output!!.destroy()
            output = null
        }
    }

    internal inner class BlurAsyncTask(private val inBitmap: Bitmap, private val outBitmap: Bitmap?,
                                       private val callback: BlurEngine.Callback) : AsyncTask<Void?, Void?, Bitmap?>() {

        override fun doInBackground(vararg params: Void?): Bitmap? {
            if (isCancelled) {
                return null
            }
            if (blurConfig.debug()) {
                Log.d(TAG, "Running in background...")
            }
            return blur(inBitmap, outBitmap)
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
        private val TAG = RenderScriptBlur::class.java.simpleName
        private var isAvailabilityChecked = false
        private var isAvailable = false
        @Synchronized fun isAvailable(context: Context): Boolean {
            if (!isAvailabilityChecked) {
                var available = true
                var rs: RenderScript? = null
                try {
                    rs = RenderScript.create(context)
                } catch (e: RSRuntimeException) {
                    Log.w(TAG, "Renderscript is not available on this device.")
                    available = false
                } finally {
                    rs?.destroy()
                    isAvailabilityChecked = true
                    isAvailable = available
                }
            }
            return isAvailable
        }
    }

    init {
        rs = RenderScript.create(context)
        scriptBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    }
}