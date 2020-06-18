package com.ms_square.etsyblur

import androidx.annotation.AnyThread

/**
 * SimpleAsyncPolicy.java
 *
 * @author Manabu-GT on 3/22/17.
 */
@AnyThread
class SimpleAsyncPolicy : AsyncPolicy {

    /**
     * Just returns true when renderScript is not available to use
     * @param isRenderScript
     * @param computation
     * @return true when renderscript is not available to use; otherwise returns false
     */
    override fun shouldAsync(isRenderScript: Boolean, computation: Long): Boolean {
        return !isRenderScript
    }

    override fun putSampleData(isRenderScript: Boolean, computation: Long, timeInNanos: Long) {
        // does nothing
    }
}