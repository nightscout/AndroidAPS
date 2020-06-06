package com.ms_square.etsyblur;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * BlurEngine.java
 *
 * @author Manabu-GT on 3/22/17.
 */
public interface BlurEngine {

    /**
     * Applies the blur effect synchronously based on the given inBitmap.
     * If canReuseInBitmap is true, it applies the blur effect on the inBitmap itself; otherwise,
     * it internally creates a new mutable bitmap and returns it after the blur.
     * @param inBitmap
     * @param canReuseInBitmap
     * @return Bitmap
     */
    @Nullable
    Bitmap execute(@NonNull Bitmap inBitmap, boolean canReuseInBitmap);

    /**
     * Applies the blur effect synchronously based on the given inBitmap.
     * outBitmap will be used to write the blurred bitmap image.
     * @param inBitmap
     * @param outBitmap
     * @return Bitmap
     */
    @Nullable
    Bitmap execute(@NonNull Bitmap inBitmap, @NonNull Bitmap outBitmap);

    /**
     * Based on the given {@link AsyncPolicy} through {@link BlurConfig}, it dynamically decides whether to execute
     * the blur effect in background thread or not. Thus, callback could be called immediately
     * on the calling ui thread if the AsyncPolicy decides to execute synchronously.
     * @param inBitmap
     * @param canReuseInBitmap
     * @param callback
     */
    void execute(@NonNull Bitmap inBitmap, boolean canReuseInBitmap, @NonNull Callback callback);

    /**
     * Based on the given {@link AsyncPolicy} through {@link BlurConfig}, it dynamically decides whether to execute
     * the blur effect in background thread or not. Thus, callback could be called immediately
     * on the calling ui thread if the AsyncPolicy decides to execute synchronously.
     * @param inBitmap
     * @param outBitmap
     * @param callback
     */
    void execute(@NonNull Bitmap inBitmap, @NonNull Bitmap outBitmap, @NonNull Callback callback);

    /**
     * Destroys resources used for this {@link BlurEngine}.
     * After you call this method, any operation on the {@link BlurEngine} could result in an error.
     */
    void destroy();

    /**
     * Returns human readable string which shortly describes the method used to perform blur
     * @return String - (ex...RenderScript's ScriptIntrinsicBlur, Java's FastBlur implementation...etc)
     */
    @NonNull
    String methodDescription();

    interface Callback {
        /**
         * Called when the blur operation is finished.
         * It is possible that returned bitmap is null in case of an error during the operation.
         * @param blurredBitmap
         */
        @UiThread
        void onFinished(@Nullable Bitmap blurredBitmap);
    }
}
