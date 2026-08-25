package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.interaction.utils.DisplayFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [IobDetailedComplication] and the shared [ModernBaseComplicationProviderService] logic it
 * inherits: [getPreviewData]/[getPreviewComplicationData] (sample data + tap intent → build) and the
 * action/name accessors. Built via [Robolectric] so a Context is attached without running onCreate's
 * Dagger injection; the `@Inject` fields (including [DisplayFormat], used by buildComplicationData)
 * are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class IobDetailedComplicationTest {

    private fun sut(): IobDetailedComplication =
        Robolectric.buildService(IobDetailedComplication::class.java).get().also {
            it.aapsLogger = AAPSLoggerTest()
            it.displayFormat = DisplayFormat().also { d -> d.sp = mock(); d.context = it }
        }

    @Test
    fun `preview builds a short-text complication for the sample IOB`() {
        val data = sut().getPreviewData(ComplicationType.SHORT_TEXT)

        assertThat(data).isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.RANGED_VALUE)).isNull()
    }

    @Test
    fun `tapping the IOB detailed complication opens the bolus wizard`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.BOLUS)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("IobDetailedComplication")
    }
}
