package com.ms_square.etsyblur;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * ViewUtil.java
 *
 * @author Manabu-GT on 6/12/14.
 */
public final class ViewUtil {

    public static final boolean IS_POST_HONEYCOMB_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;

    public static Bitmap drawViewToBitmap(View view, int width, int height, int downScaleFactor, @ColorInt int overlayColor) {
        return drawViewToBitmap(view, width, height, 0f, 0f, downScaleFactor, overlayColor);
    }

    public static Bitmap drawViewToBitmap(View view, int width, int height, float translateX,
                                          float translateY, int downScaleFactor, @ColorInt int overlayColor) {
        if (downScaleFactor <= 0) {
            throw new IllegalArgumentException("downSampleFactor must be greater than 0.");
        }

        // check whether valid width/height is given to create a bitmap
        if (width <= 0 || height <= 0) {
            return null;
        }

        int bmpWidth = (int) ((width - translateX) / downScaleFactor);
        int bmpHeight = (int) ((height - translateY) / downScaleFactor);

        Bitmap dest = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas(dest);
        c.translate(-translateX / downScaleFactor, -translateY / downScaleFactor);
        c.scale(1f / downScaleFactor, 1f / downScaleFactor);
        view.draw(c);

        if (overlayColor != Color.TRANSPARENT) {
            Paint paint = new Paint();
            paint.setFlags(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(overlayColor);
            c.drawPaint(paint);
        }

        return dest;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public static void animateAlpha(@NonNull View view, float fromAlpha, float toAlpha, int duration,
                                    final Runnable endAction) {
        if (IS_POST_HONEYCOMB_MR1) {
            ViewPropertyAnimator animator = view.animate().alpha(toAlpha).setDuration(duration);
            if (endAction != null) {
                animator.setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        endAction.run();
                    }
                });
            }
        } else {
            AlphaAnimation alphaAnimation = new AlphaAnimation(fromAlpha, toAlpha);
            alphaAnimation.setDuration(duration);
            alphaAnimation.setFillAfter(true);
            if (endAction != null) {
                alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        // fixes the crash bug while removing views
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(endAction);
                    }
                    @Override
                    public void onAnimationStart(Animation animation) { }
                    @Override
                    public void onAnimationRepeat(Animation animation) { }
                });
            }
            view.startAnimation(alphaAnimation);
        }
    }
}