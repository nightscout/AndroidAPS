package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [BgGraphComplication]: the synthetic sine-curve preview graph, the image build path
 * for both supported types (which exercises the full bitmap render via `renderBgGraph`), the
 * unsupported-type fallback and the action/name accessors. Built via [Robolectric] so a Context
 * is attached without running onCreate's Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class BgGraphComplicationTest {

    private fun sut(): BgGraphComplication =
        Robolectric.buildService(BgGraphComplication::class.java).get().also {
            it.aapsLogger = AAPSLoggerTest()
            it.sp = mock()
            whenever(it.sp.getString("complication_bg_graph_hours", "3")).thenReturn("3")
        }

    @Test
    fun `preview builds a small-image complication with a rendered graph`() {
        val data = sut().getPreviewData(ComplicationType.SMALL_IMAGE)

        assertThat(data).isInstanceOf(SmallImageComplicationData::class.java)
    }

    @Test
    fun `preview builds a photo-image complication with a rendered graph`() {
        val data = sut().getPreviewData(ComplicationType.PHOTO_IMAGE)

        assertThat(data).isInstanceOf(PhotoImageComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `the sample preview data carries a realistic synthetic BG curve`() {
        val entries = sut().getPreviewComplicationData().graphData.entries

        assertThat(entries).hasSize(36)
        for (entry in entries) {
            // 130 ± 45 sine curve with 180/70 thresholds
            assertThat(entry.sgv).isAtLeast(85.0)
            assertThat(entry.sgv).isAtMost(175.0)
            assertThat(entry.high).isEqualTo(180.0)
            assertThat(entry.low).isEqualTo(70.0)
        }
        // Entries are ordered oldest-first in 5-minute steps ending now
        for (i in 1 until entries.size) {
            assertThat(entries[i].timeStamp - entries[i - 1].timeStamp).isEqualTo(5 * 60_000L)
        }
    }

    @Test
    fun `tapping the BG graph complication opens the bg graph`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.BG_GRAPH)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("BgGraphComplication")
    }
}
