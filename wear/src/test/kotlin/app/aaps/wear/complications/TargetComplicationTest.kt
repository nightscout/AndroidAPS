package app.aaps.wear.complications

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
 * Covers [TargetComplication] and the shared [ModernBaseComplicationProviderService] logic it
 * inherits: [getPreviewData]/[getPreviewComplicationData] (sample temp-target data + tap intent →
 * build) and the action/name accessors. Built via [Robolectric] so a Context is attached without
 * running onCreate's Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class TargetComplicationTest {

    private fun sut(): TargetComplication =
        Robolectric.buildService(TargetComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `preview builds a short-text complication for the sample target`() {
        val data = sut().getPreviewData(ComplicationType.SHORT_TEXT)

        assertThat(data).isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.RANGED_VALUE)).isNull()
    }

    @Test
    fun `the sample preview data carries an active temp target`() {
        val preview = sut().getPreviewComplicationData()

        assertThat(preview.statusData.tempTarget).isEqualTo("6.6")
        assertThat(preview.statusData.tempTargetDuration).isEqualTo(90 * 60_000L)
    }

    @Test
    fun `tapping the target complication opens the temp target screen`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.TEMP_TARGET)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("TargetComplication")
    }
}
