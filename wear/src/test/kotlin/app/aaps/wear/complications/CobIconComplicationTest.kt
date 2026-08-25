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
 * Covers [CobIconComplication] and the shared [ModernBaseComplicationProviderService] logic it
 * inherits: [getPreviewData]/[getPreviewComplicationData] (sample data + tap intent → build) and the
 * action/name accessors. Built via [Robolectric] so a Context is attached without running onCreate's
 * Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class CobIconComplicationTest {

    private fun sut(): CobIconComplication =
        Robolectric.buildService(CobIconComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `preview builds a short-text complication for the sample COB`() {
        val data = sut().getPreviewData(ComplicationType.SHORT_TEXT)

        assertThat(data).isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.RANGED_VALUE)).isNull()
    }

    @Test
    fun `tapping the COB icon complication opens the wizard`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.WIZARD)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("CobIconComplication")
    }
}
