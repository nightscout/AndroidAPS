package com.ms_square.etsyblur

/**
 * AlwaysAsyncPolicy.java
 *
 * @author Manabu-GT on 3/24/17.
 */
class AlwaysAsyncPolicy : AsyncPolicy {

    override fun shouldAsync(isRenderScript: Boolean, computation: Long): Boolean {
        return true
    }

    override fun putSampleData(isRenderScript: Boolean, computation: Long, timeInNanos: Long) {
        // does nothing
    }
}