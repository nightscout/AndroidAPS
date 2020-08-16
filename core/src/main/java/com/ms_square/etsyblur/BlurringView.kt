/**
 * Copyright (c) 2015 500px Inc.
 * Copyright 2017 Manabu-GT
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.ms_square.etsyblur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import com.ms_square.etsyblur.BlurConfig
import com.ms_square.etsyblur.BlurringView
import info.nightscout.androidaps.core.R

class BlurringView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var blurConfig: BlurConfig?
    private var blur: BlurEngine? = null
    private var blurredView: View? = null
    private var blurredViewWidth = 0
    private var blurredViewHeight = 0
    private var bitmapToBlur: Bitmap? = null
    private var blurringCanvas: Canvas? = null

    /**
     * Flag used to prevent draw() from being recursively called when blurredView is set to the parent view
     */
    private var parentViewDrawn = false
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        checkNotNull(blurConfig) { "BlurConfig must be set before onAttachedToWindow() gets called." }
        blur = if (isInEditMode) {
            NoBlur()
        } else {
            Blur(context, blurConfig!!)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (blurredView != null && blurredView!!.viewTreeObserver.isAlive) {
            blurredView!!.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        blur!!.destroy()
    }

    override fun onDraw(canvas: Canvas) {
        val isParent = blurredView === parent
        if (isParent) {
            if (parentViewDrawn) {
                return
            }
            parentViewDrawn = true
        }
        if (blurredView != null) {
            if (prepare()) {
                // If the background of the blurred view is a color drawable, we use it to clear
                // the blurring canvas, which ensures that edges of the child views are blurred
                // as well; otherwise we clear the blurring canvas with a transparent color.
                if (blurredView!!.background != null && blurredView!!.background is ColorDrawable) {
                    bitmapToBlur!!.eraseColor((blurredView!!.background as ColorDrawable).color)
                } else {
                    bitmapToBlur!!.eraseColor(Color.TRANSPARENT)
                }
                blurringCanvas!!.save()
                blurringCanvas!!.translate(-blurredView!!.scrollX.toFloat(), -blurredView!!.scrollY.toFloat())
                blurredView!!.draw(blurringCanvas)
                blurringCanvas!!.restore()
                val blurred = blur!!.execute(bitmapToBlur!!, true)
                if (blurred != null) {
                    canvas.save()
                    canvas.translate(blurredView!!.x - x, blurredView!!.y - y)
                    canvas.scale(blurConfig!!.downScaleFactor().toFloat(), blurConfig!!.downScaleFactor().toFloat())
                    canvas.drawBitmap(blurred, 0f, 0f, null)
                    canvas.restore()
                }
                if (blurConfig!!.overlayColor() != Color.TRANSPARENT) {
                    canvas.drawColor(blurConfig!!.overlayColor())
                }
            }
        }
        if (isParent) {
            parentViewDrawn = false
        }
    }

    fun blurredView(blurredView: View) {
        if (this.blurredView != null && this.blurredView !== blurredView) {
            if (this.blurredView!!.viewTreeObserver.isAlive) {
                this.blurredView!!.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            }
        }
        this.blurredView = blurredView
        if (this.blurredView!!.viewTreeObserver.isAlive) {
            this.blurredView!!.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        }
    }

    fun blurConfig(blurConfig: BlurConfig) {
        check(blur == null) { "BlurConfig must be set before onAttachedToWindow() gets called." }
        this.blurConfig = blurConfig
    }

    private fun prepare(): Boolean {
        val newWidth = blurredView!!.width
        val newHeight = blurredView!!.height
        if (newWidth != blurredViewWidth || newHeight != blurredViewHeight) {
            blurredViewWidth = newWidth
            blurredViewHeight = newHeight
            val downScaleFactor = blurConfig!!.downScaleFactor()
            val scaledWidth = newWidth / downScaleFactor
            val scaledHeight = newHeight / downScaleFactor
            if (bitmapToBlur == null || scaledWidth != bitmapToBlur!!.width || scaledHeight != bitmapToBlur!!.height) {

                // check whether valid width/height is given to create a bitmap
                if (scaledWidth <= 0 || scaledHeight <= 0) {
                    return false
                }
                bitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                if (bitmapToBlur == null) {
                    return false
                }
            }
            blurringCanvas = Canvas(bitmapToBlur as Bitmap)
            blurringCanvas!!.scale(1f / downScaleFactor, 1f / downScaleFactor)
        }
        return true
    }

    val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (!isDirty && blurredView!!.isDirty && isShown) {
            // blurredView is dirty, but BlurringView is not dirty and shown; thus, call invalidate to force re-draw
            invalidate()
        }
        true
    }

    companion object {
        private val TAG = BlurringView::class.java.simpleName
    }

    init {
        val typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.BlurringView)
        val overlayColor = typedArray.getInt(R.styleable.BlurringView_overlayColor, BlurConfig.DEFAULT_OVERLAY_COLOR)
        val blurRadius = typedArray.getInt(R.styleable.BlurringView_radius, BlurConfig.DEFAULT_RADIUS)
        val downScaleFactor = typedArray.getInt(R.styleable.BlurringView_downScaleFactor, BlurConfig.DEFAULT_DOWN_SCALE_FACTOR)
        val allowFallback = typedArray.getBoolean(R.styleable.BlurringView_allowFallback, BlurConfig.DEFAULT_ALLOW_FALLBACK)
        val debug = typedArray.getBoolean(R.styleable.BlurringView_debug, BlurConfig.DEFAULT_DEBUG)
        typedArray.recycle()
        blurConfig = BlurConfig.Builder()
            .radius(blurRadius)
            .downScaleFactor(downScaleFactor)
            .allowFallback(allowFallback)
            .overlayColor(overlayColor)
            .debug(debug)
            .build()
    }
}