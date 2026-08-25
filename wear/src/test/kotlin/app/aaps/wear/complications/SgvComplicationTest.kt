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
 * Covers [SgvComplication] and the shared [ModernBaseComplicationProviderService] logic it inherits:
 * [getPreviewData]/[getPreviewComplicationData] (sample data + tap intent → build), the
 * no-sync/outdated fallbacks and the action/name accessors. Built via [Robolectric] so a Context is
 * attached without running onCreate's Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class SgvComplicationTest {

    private fun sut(): SgvComplication =
        Robolectric.buildService(SgvComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    private fun pendingIntent(sut: SgvComplication): PendingIntent =
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
    fun `the sample preview data carries realistic glucose and status values`() {
        val preview = sut().getPreviewComplicationData()

        assertThat(preview.bgData.sgvString).isEqualTo("120")
        assertThat(preview.statusData.cob).isEqualTo("15g")
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
    fun `tapping the SGV complication opens loop status`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.LOOP_STATUS)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("SgvComplication")
    }
}
