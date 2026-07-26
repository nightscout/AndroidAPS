package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
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
 * Covers [LongStatusComplication] and the shared [ModernBaseComplicationProviderService] logic it
 * inherits: [getPreviewData]/[getPreviewComplicationData] (sample data + tap intent → build) and the
 * action/name accessors. Built via [Robolectric] so a Context is attached without running onCreate's
 * Dagger injection; the `@Inject` fields are set directly. This complication renders LONG_TEXT via
 * [DisplayFormat], so a DisplayFormat with a mocked SP and the service as Context is wired in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class LongStatusComplicationTest {

    private fun sut(): LongStatusComplication =
        Robolectric.buildService(LongStatusComplication::class.java).get().also {
            it.aapsLogger = AAPSLoggerTest()
            it.displayFormat = DisplayFormat().also { d ->
                d.sp = mock()
                d.context = it
            }
        }

    @Test
    fun `preview builds a long-text complication for the sample status`() {
        val data = sut().getPreviewData(ComplicationType.LONG_TEXT)

        assertThat(data).isInstanceOf(LongTextComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `tapping the long status complication opens the menu`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.MENU)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("LongStatusComplication")
    }
}
