package com.ms_square.etsyblur;

/**
 * AlwaysAsyncPolicy.java
 *
 * @author Manabu-GT on 3/24/17.
 */
public final class AlwaysAsyncPolicy implements AsyncPolicy {

    @Override
    public boolean shouldAsync(boolean isRenderScript, long computation) {
        return true;
    }

    @Override
    public void putSampleData(boolean isRenderScript, long computation, long timeInNanos) {
        // does nothing
    }
}
