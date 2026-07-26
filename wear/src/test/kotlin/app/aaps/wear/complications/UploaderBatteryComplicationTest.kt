package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [UploaderBatteryComplication] and the shared [ModernBaseComplicationProviderService] logic
 * it inherits: [getPreviewData]/[getPreviewComplicationData] build the phone-battery complication for
 * the supported types (RANGED_VALUE / SHORT_TEXT / MONOCHROMATIC_IMAGE / SMALL_IMAGE), the else branch
 * yields null, and the action/name accessors resolve. Built via [Robolectric] so a Context is attached
 * without running onCreate's Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class UploaderBatteryComplicationTest {

    private fun sut(): UploaderBatteryComplication =
        Robolectric.buildService(UploaderBatteryComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `preview builds a ranged-value complication for the sample battery`() {
        assertThat(sut().getPreviewData(ComplicationType.RANGED_VALUE)).isInstanceOf(RangedValueComplicationData::class.java)
    }

    @Test
    fun `preview builds a short-text complication for the sample battery`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `preview builds a monochromatic-image complication for the sample battery`() {
        assertThat(sut().getPreviewData(ComplicationType.MONOCHROMATIC_IMAGE)).isInstanceOf(MonochromaticImageComplicationData::class.java)
    }

    @Test
    fun `preview builds a small-image complication for the sample battery`() {
        assertThat(sut().getPreviewData(ComplicationType.SMALL_IMAGE)).isInstanceOf(SmallImageComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.LONG_TEXT)).isNull()
    }

    @Test
    fun `tapping the uploader battery complication opens the main menu`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.MENU)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("UploaderBatteryComplication")
    }
}
