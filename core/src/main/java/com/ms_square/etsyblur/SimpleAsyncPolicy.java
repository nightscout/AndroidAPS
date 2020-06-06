package com.ms_square.etsyblur;


import androidx.annotation.AnyThread;

/**
 * SimpleAsyncPolicy.java
 *
 * @author Manabu-GT on 3/22/17.
 */
@AnyThread
public class SimpleAsyncPolicy implements AsyncPolicy {

    /**
     * Just returns true when renderScript is not available to use
     * @param isRenderScript
     * @param computation
     * @return true when renderscript is not available to use; otherwise returns false
     */
    @Override
    public boolean shouldAsync(boolean isRenderScript, long computation) {
        return !isRenderScript;
    }

    @Override
    public void putSampleData(boolean isRenderScript, long computation, long timeInNanos) {
        // does nothing
    }
}
