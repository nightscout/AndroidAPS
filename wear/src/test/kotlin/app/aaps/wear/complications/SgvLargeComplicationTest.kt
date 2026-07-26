package app.aaps.wear.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [SgvLargeComplication] and the shared [ModernBaseComplicationProviderService] logic it
 * inherits: [getPreviewData]/[getPreviewComplicationData] (sample data + tap intent → build), the
 * no-sync/outdated fallbacks and the action/name accessors. Built via [Robolectric] so a Context is
 * attached without running onCreate's Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class SgvLargeComplicationTest {

    private fun sut(): SgvLargeComplication =
        Robolectric.buildService(SgvLargeComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    private fun pendingIntent(sut: SgvLargeComplication): PendingIntent =
        PendingIntent.getActivity(sut, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    @Test
    fun `preview builds a short-text complication for the sample glucose`() {
        val data = sut().getPreviewData(ComplicationType.SHORT_TEXT)

        assertThat(data).isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.RANGED_VALUE)).isNull()
    }

    @Test
    fun `no-sync and outdated data fall back to the normal builder`() {
        val sut = sut()
        val data = sut.getPreviewComplicationData()
        val pi = pendingIntent(sut)

        assertThat(sut.buildNoSyncComplicationData(ComplicationType.SHORT_TEXT, data, pi))
            .isInstanceOf(ShortTextComplicationData::class.java)
        assertThat(sut.buildOutdatedComplicationData(ComplicationType.SHORT_TEXT, data, pi))
            .isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `tapping the large SGV complication opens the bg graph`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.BG_GRAPH)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("SgvLargeComplication")
    }
}
