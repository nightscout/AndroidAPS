package app.aaps.wear.complications.cwf

import app.aaps.wear.watchfaces.CustomWatchface.RenderLayer.Refresh

/**
 * What the cached layers were built from.
 *
 * @param styleId which design the watch face carried - see `CustomWatchface.styleId`
 * @param minute the minute they depict, for the layers that show it
 */
data class CachedLayers(val styleId: Int, val minute: Long)

/**
 * Decides which layers have to be drawn again, and nothing else.
 *
 * Pure arithmetic on purpose. The decision used to be spread across the pipeline, the updater and an
 * event subscription, and each of them could be right on its own while the result was wrong. Three
 * faults came out of that, all with the same shape - something changed, and the part that should have
 * noticed was not the part that was asked:
 *
 * - a wrist raise threw away layers that were still perfectly good, so waking rebuilt everything;
 * - an ambient frame reloaded the data and cleared the "stale" flag without rebuilding anything, so
 *   the next frame reused layers from the previous zip;
 * - a newly sent zip was noticed by nobody at all, and the wearer kept the old background behind the
 *   new watch face until the next glucose reading - with only the layers rebuilt every minute
 *   following the change, which is why the cover plate and the hands moved and nothing else did.
 *
 * The rule that prevents all three is that **the cache carries what it was built from**, and is
 * compared rather than notified.
 */
object CwfCachePolicy {

    /**
     * The refresh classes whose layers must be drawn again, empty when the cache still stands.
     *
     * [Refresh.TICK] never appears: those layers are drawn on every frame and never cached.
     */
    fun toRebuild(styleId: Int, minute: Long, cached: CachedLayers?, dataStale: Boolean): Set<Refresh> = when {
        // Nothing to reuse yet
        cached == null            -> setOf(Refresh.DATA, Refresh.MINUTE)
        // The design itself changed: every layer belongs to the previous one
        cached.styleId != styleId -> setOf(Refresh.DATA, Refresh.MINUTE)
        // New values arrived; they reach layers in both classes, so both are redrawn rather than
        // guessing which ones the data touched
        dataStale                 -> setOf(Refresh.DATA, Refresh.MINUTE)
        // Only the clock moved on
        cached.minute != minute   -> setOf(Refresh.MINUTE)
        else                      -> emptySet()
    }
}
