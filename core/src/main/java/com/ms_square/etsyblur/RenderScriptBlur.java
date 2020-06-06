package com.ms_square.etsyblur;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Debug;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * RenderScriptBlur.java
 *
 * @author Manabu-GT on 3/22/17.
 */
class RenderScriptBlur extends BaseBlurEngine {

    private static final String TAG = RenderScriptBlur.class.getSimpleName();

    private static boolean isAvailabilityChecked;
    private static boolean isAvailable;

    private final Object LOCK = new Object();

    private final RenderScript rs;
    private final ScriptIntrinsicBlur scriptBlur;
    private Allocation input;
    private Allocation output;

    private int width;
    private int height;

    public RenderScriptBlur(@NonNull Context context, @NonNull BlurConfig blurConfig) {
        super(blurConfig);
        rs = RenderScript.create(context);
        scriptBlur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
    }

    public synchronized static boolean isAvailable(@NonNull Context context) {
        if (!isAvailabilityChecked) {
            boolean available = true;
            RenderScript rs = null;
            try {
                rs = RenderScript.create(context);
            } catch (RSRuntimeException e) {
                Log.w(TAG, "Renderscript is not available on this device.");
                available = false;
            } finally {
                if (rs != null) {
                    rs.destroy();
                }
                isAvailabilityChecked = true;
                isAvailable = available;
            }
        }
        return isAvailable;
    }

    @Override
    public Bitmap execute(@NonNull Bitmap inBitmap, boolean canReuseInBitmap) {
        return blur(inBitmap, canReuseInBitmap ? inBitmap : inBitmap.copy(inBitmap.getConfig(), true));
    }

    @Override
    public Bitmap execute(@NonNull Bitmap inBitmap, @NonNull Bitmap outBitmap) {
        return blur(inBitmap, outBitmap);
    }

    @Override
    public void execute(@NonNull Bitmap inBitmap, boolean canReuseInBitmap, @NonNull Callback callback) {
        if (shouldAsync(inBitmap.getWidth(), inBitmap.getHeight(), blurConfig.radius())) {
            asyncTasks.add(new BlurAsyncTask(inBitmap,
                    canReuseInBitmap ? inBitmap : inBitmap.copy(inBitmap.getConfig(), true), callback).execute());
        } else {
            callback.onFinished(execute(inBitmap, canReuseInBitmap));
        }
    }

    @Override
    public void execute(@NonNull Bitmap inBitmap, @NonNull Bitmap outBitmap, @NonNull Callback callback) {
        if (shouldAsync(inBitmap.getWidth(), inBitmap.getHeight(), blurConfig.radius())) {
            asyncTasks.add(new BlurAsyncTask(inBitmap, outBitmap, callback).execute());
        } else {
            callback.onFinished(blur(inBitmap, outBitmap));
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        synchronized (LOCK) {
            // clean up renderscript resources
            if (rs != null) {
                rs.destroy();
            }
            if (scriptBlur != null) {
                scriptBlur.destroy();
            }
            destroyInputOutput();
        }
    }

    @Override
    public String methodDescription() {
        return "RenderScript's ScriptIntrinsicBlur";
    }

    @Override
    long calculateComputation(int bmpWidth, int bmpHeight, int radius) {
        return bmpWidth * bmpHeight;
    }

    @Override
    boolean shouldAsync(int bmpWidth, int bmpHeight, int radius) {
        return blurConfig.asyncPolicy().shouldAsync(true, calculateComputation(bmpWidth, bmpHeight, radius));
    }

    private Bitmap blur(Bitmap inBitmap, Bitmap outBitmap) {
        long start = Debug.threadCpuTimeNanos();
        int newWidth = inBitmap.getWidth();
        int newHeight = inBitmap.getHeight();
        synchronized (LOCK) {
            if (input == null || width != newWidth || height != newHeight) {
                width = newWidth;
                height = newHeight;

                destroyInputOutput();
                input = Allocation.createFromBitmap(rs, inBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                        Allocation.USAGE_SCRIPT);
                output = Allocation.createTyped(rs, input.getType());
            }

            input.copyFrom(inBitmap);
            scriptBlur.setRadius(blurConfig.radius());
            scriptBlur.setInput(input);
            scriptBlur.forEach(output);
            output.copyTo(outBitmap);
        }

        if (start > 0) {
            long duration = Debug.threadCpuTimeNanos() - start;
            blurConfig.asyncPolicy().putSampleData(true, calculateComputation(newWidth, newHeight, blurConfig.radius()),duration);
            if (blurConfig.debug()) {
                Log.d(TAG, String.format(Locale.US, "RenderScriptBlur took %d ms.", duration / 1000000L));
            }
        }

        return outBitmap;
    }

    private void destroyInputOutput() {
        if (input != null) {
            input.destroy();
            input = null;
        }
        if (output != null) {
            output.destroy();
            output = null;
        }
    }

    class BlurAsyncTask extends AsyncTask<Void, Void, Bitmap> {

        private final Bitmap inBitmap;
        private final Bitmap outBitmap;
        private final Callback callback;

        public BlurAsyncTask(@NonNull Bitmap inBitmap, @Nullable Bitmap outBitmap,
                             @NonNull Callback callback) {
            this.inBitmap = inBitmap;
            this.outBitmap = outBitmap;
            this.callback = callback;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            if (isCancelled()) {
                return null;
            }
            if (blurConfig.debug()) {
                Log.d(TAG, "Running in background...");
            }
            return blur(inBitmap, outBitmap);
        }

        @Override
        protected void onCancelled(Bitmap blurredBitmap) {
            asyncTasks.remove(this);
        }

        @Override
        protected void onPostExecute(Bitmap blurredBitmap) {
            callback.onFinished(blurredBitmap);
            asyncTasks.remove(this);
        }
    }
}
