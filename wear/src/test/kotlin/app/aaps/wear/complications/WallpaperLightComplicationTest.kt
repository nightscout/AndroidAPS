package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [WallpaperLightComplication] and the [WallpaperComplication]/
 * [ModernBaseComplicationProviderService] logic it inherits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WallpaperLightComplicationTest {

    private fun sut(): WallpaperLightComplication =
        Robolectric.buildService(WallpaperLightComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `tapping a wallpaper complication performs no action`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.NONE)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("WallpaperLightComplication")
    }
}
