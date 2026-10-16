package app.aaps.wear.complications.cwf

import app.aaps.wear.watchfaces.CustomWatchface.RenderLayer.Refresh
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * When the cached layers have to be drawn again.
 *
 * Every case here is a fault seen on a watch, not an invented one. The most expensive was a newly
 * sent zip: the cover plate and the hands changed to the new design while everything behind them kept
 * the old one, for minutes, because the layers that follow the minute were rebuilt and the rest were
 * not.
 */
class CwfCachePolicyTest {

    private val bothClasses = setOf(Refresh.DATA, Refresh.MINUTE)

    @Test
    fun `everything, when nothing is cached yet`() {
        assertThat(CwfCachePolicy.toRebuild(styleId = 1, minute = 100, cached = null, dataStale = false))
            .isEqualTo(bothClasses)
    }

    @Test
    fun `everything, when the design itself changed`() {
        // The zip switch: the cache belongs to the previous design, all of it
        val cached = CachedLayers(styleId = 1, minute = 100)

        assertThat(CwfCachePolicy.toRebuild(styleId = 2, minute = 100, cached = cached, dataStale = false))
            .isEqualTo(bothClasses)
    }

    @Test
    fun `everything, when new values arrived`() {
        val cached = CachedLayers(styleId = 1, minute = 100)

        assertThat(CwfCachePolicy.toRebuild(styleId = 1, minute = 100, cached = cached, dataStale = true))
            .isEqualTo(bothClasses)
    }

    @Test
    fun `only the layers that show the minute, when only the minute turned`() {
        val cached = CachedLayers(styleId = 1, minute = 100)

        assertThat(CwfCachePolicy.toRebuild(styleId = 1, minute = 101, cached = cached, dataStale = false))
            .containsExactly(Refresh.MINUTE)
    }

    @Test
    fun `nothing, when neither the design nor the data nor the minute moved`() {
        val cached = CachedLayers(styleId = 1, minute = 100)

        assertThat(CwfCachePolicy.toRebuild(styleId = 1, minute = 100, cached = cached, dataStale = false))
            .isEmpty()
    }

    @Test
    fun `a design change is never answered by rebuilding only the minute layers`() {
        // This exact half-measure is what the wearer saw: the cover plate and hands followed the new
        // zip while the background stayed on the old one
        val cached = CachedLayers(styleId = 1, minute = 100)

        assertThat(CwfCachePolicy.toRebuild(styleId = 2, minute = 101, cached = cached, dataStale = false))
            .contains(Refresh.DATA)
    }

    @Test
    fun `waking does not enter into it`() {
        // The mode is absent from this decision on purpose: the cached layers hold no seconds, so they
        // are the same awake and dozing. Treating a wrist raise as a reason to rebuild made waking
        // cost a full rebuild for nothing.
        val cached = CachedLayers(styleId = 7, minute = 500)

        assertThat(CwfCachePolicy.toRebuild(styleId = 7, minute = 500, cached = cached, dataStale = false))
            .isEmpty()
    }
}
