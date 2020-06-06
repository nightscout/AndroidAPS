package com.ms_square.etsyblur;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Blur.java
 *
 * @author Manabu-GT on 3/22/17.
 */
public class Blur implements BlurEngine {

    private static final String TAG = Blur.class.getSimpleName();

    private BlurEngine blurEngine;

    public Blur(@NonNull Context context, @NonNull BlurConfig blurConfig) {
        if (RenderScriptBlur.isAvailable(context)) {
            blurEngine = new RenderScriptBlur(context, blurConfig);
        } else if (blurConfig.allowFallback()) {
            blurEngine = new JavaFastBlur(blurConfig);
        } else {
            blurEngine = new NoBlur();
        }
        if (blurConfig.debug()) {
            Log.d(TAG, "Used Blur Method: " + blurEngine.methodDescription());
        }
    }

    @Nullable
    @Override
    public Bitmap execute(@NonNull Bitmap inBitmap, boolean canReuseInBitmap) {
        return blurEngine.execute(inBitmap, canReuseInBitmap);
    }

    @Nullable
    @Override
    public Bitmap execute(@NonNull Bitmap inBitmap, @NonNull Bitmap outBitmap) {
        return blurEngine.execute(inBitmap, outBitmap);
    }

    @Override
    public void execute(@NonNull Bitmap inBitmap, boolean canReuseInBitmap, @NonNull Callback callback) {
        blurEngine.execute(inBitmap, canReuseInBitmap, callback);
    }

    @Override
    public void execute(@NonNull Bitmap inBitmap, @NonNull Bitmap outBitmap, @NonNull Callback callback) {
        blurEngine.execute(inBitmap, outBitmap, callback);
    }

    @Override
    public void destroy() {
        blurEngine.destroy();
    }

    @NonNull
    @Override
    public String methodDescription() {
        return blurEngine.methodDescription();
    }
}
