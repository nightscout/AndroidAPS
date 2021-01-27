package com.ms_square.etsyblur

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.annotation.ColorInt

/**
 * ViewUtil.java
 *
 * @author Manabu-GT on 6/12/14.
 */
object ViewUtil {

    val IS_POST_HONEYCOMB_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1
    fun drawViewToBitmap(view: View, width: Int, height: Int, downScaleFactor: Int, @ColorInt overlayColor: Int): Bitmap? {
        return drawViewToBitmap(view, width, height, 0f, 0f, downScaleFactor, overlayColor)
    }

    fun drawViewToBitmap(view: View, width: Int, height: Int, translateX: Float,
                         translateY: Float, downScaleFactor: Int, @ColorInt overlayColor: Int): Bitmap? {
        require(downScaleFactor > 0) { "downSampleFactor must be greater than 0." }

        // check whether valid width/height is given to create a bitmap
        if (width <= 0 || height <= 0) {
            return null
        }
        val bmpWidth = ((width - translateX) / downScaleFactor).toInt()
        val bmpHeight = ((height - translateY) / downScaleFactor).toInt()
        val dest = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(dest)
        c.translate(-translateX / downScaleFactor, -translateY / downScaleFactor)
        c.scale(1f / downScaleFactor, 1f / downScaleFactor)
        view.draw(c)
        if (overlayColor != Color.TRANSPARENT) {
            val paint = Paint()
            paint.flags = Paint.ANTI_ALIAS_FLAG
            paint.color = overlayColor
            c.drawPaint(paint)
        }
        return dest
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    fun animateAlpha(view: View, fromAlpha: Float, toAlpha: Float, duration: Int,
                     endAction: (() -> Unit)?) {
        if (IS_POST_HONEYCOMB_MR1) {
            val animator = view.animate().alpha(toAlpha).setDuration(duration.toLong())
            if (endAction != null) {
                animator.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        endAction.run()
                    }
                })
            }
        } else {
            val alphaAnimation = AlphaAnimation(fromAlpha, toAlpha)
            alphaAnimation.duration = duration.toLong()
            alphaAnimation.fillAfter = true
            if (endAction != null) {
                alphaAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationEnd(animation: Animation) {
                        // fixes the crash bug while removing views
                        val handler = Handler(Looper.getMainLooper())
                        handler.post(endAction)
                    }

                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationRepeat(animation: Animation) {}
                })
            }
            view.startAnimation(alphaAnimation)
        }
    }
}

private fun <R> Function<R>?.run() {

}
