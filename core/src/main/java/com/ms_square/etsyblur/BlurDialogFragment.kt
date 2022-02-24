package com.ms_square.etsyblur

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import dagger.android.support.DaggerDialogFragment

/**
 * BlurDialogFragment.java
 *
 * @author Manabu-GT on 3/17/17.
 */
abstract class BlurDialogFragment : DaggerDialogFragment() {

    private var blur: Blur? = null
    private var root: ViewGroup? = null
    private var blurImgView: ImageView? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        blur = Blur(context, blurConfig())
        if (context is Activity) {
            root = context.window.decorView as ViewGroup
            if (root!!.isShown) {
                setUpBlurringViews()
                startEnterAnimation()
            } else {
                root!!.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        } else {
            Log.w(TAG, "onAttach(Context context) - context is not type of Activity. Currently Not supported.")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // will be only called if onCreateView returns non-null view
        // set to dismiss when touched outside content view
        view.setOnTouchListener { v, _ ->
            v.setOnTouchListener(null)
            dismiss()
            true
        }
    }

    override fun onStart() {
        val dialog = dialog
        if (dialog != null) {
            if (!backgroundDimmingEnabled()) {
                dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        super.onStart()
    }

    override fun onDismiss(dialog: DialogInterface) {
        startExitAnimation()
        super.onDismiss(dialog)
    }

    override fun onDetach() {
        root!!.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        blur!!.destroy()
        super.onDetach()
    }

    /**
     * Configuration object for the blur effect.
     * If not overwritten, it just returns [BlurConfig.DEFAULT_CONFIG] which uses [SimpleAsyncPolicy].
     * @return blur operation configuration
     */
    protected fun blurConfig(): BlurConfig {
        return BlurConfig.DEFAULT_CONFIG
    }

    /**
     * Controls if everything behind this window will be dimmed.
     *
     * @return true if dimming should be enabled
     */
    protected fun backgroundDimmingEnabled(): Boolean {
        return DEFAULT_BACKGROUND_DIMMING_ENABLED
    }

    /**
     * Alpha animation duration (ms) of the blurred image added in this fragment's hosting activity.
     *
     * @return animation duration in ms
     */
    protected fun animDuration(): Int {
        return DEFAULT_ANIM_DURATION
    }

    private fun setUpBlurringViews() {
        val visibleFrame = Rect()
        root!!.getWindowVisibleDisplayFrame(visibleFrame)
        val params = FrameLayout.LayoutParams(visibleFrame.right - visibleFrame.left,
            visibleFrame.bottom - visibleFrame.top)
        params.setMargins(visibleFrame.left, visibleFrame.top, 0, 0)
        blurImgView = ImageView(root!!.context)
        blurImgView!!.layoutParams = params
        blurImgView!!.alpha = 0f
        root!!.addView(blurImgView)

        // apply blur effect
        val bitmapToBlur = ViewUtil.drawViewToBitmap(root!!, visibleFrame.right,
            visibleFrame.bottom, visibleFrame.left.toFloat(), visibleFrame.top.toFloat(), blurConfig().downScaleFactor(),
            blurConfig().overlayColor())
        if (bitmapToBlur != null) {
            blur!!.execute(bitmapToBlur, true, object : BlurEngine.Callback {
                override fun onFinished(blurredBitmap: Bitmap?) {
                    blurImgView!!.setImageBitmap(blurredBitmap)
                }
            })
        }
    }

    private fun startEnterAnimation() {
        if (blurImgView != null) {
            ViewUtil.animateAlpha(blurImgView!!, 0f, 1f, animDuration(), null)
        }
    }

    private fun startExitAnimation() {
        if (blurImgView != null) {
            ViewUtil.animateAlpha(blurImgView!!, 1f, 0f, animDuration()) { root!!.removeView(blurImgView) }
        }
    }

    private val preDrawListener: ViewTreeObserver.OnPreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            root!!.viewTreeObserver.removeOnPreDrawListener(this)
            // makes sure to get the complete drawing after the layout pass
            root!!.post {
                setUpBlurringViews()
                startEnterAnimation()
            }
            return true
        }
    }

    companion object {
        private val TAG = BlurDialogFragment::class.java.simpleName
        const val DEFAULT_ANIM_DURATION = 400
        const val DEFAULT_BACKGROUND_DIMMING_ENABLED = false
    }
}