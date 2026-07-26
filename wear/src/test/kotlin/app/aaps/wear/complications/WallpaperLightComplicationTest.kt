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
 *
 * The only supported type is [ComplicationType.PHOTO_IMAGE], whose builder reads the
 * `watch_light.jpg` asset and scales a bitmap using `WindowManager.currentWindowMetrics`;
 * that path is not exercised here because it touches real asset/bitmap/window-metrics code
 * (and only `IOException` is caught, so it is not safely reproducible under Robolectric).
 * Instead we verify the genuine `else -> null` branch of the preview pipeline plus the
 * overridden tap action ([ComplicationAction.NONE]) and provider canonical name.
 * Built via [Robolectric] so a Context is attached without running onCreate's Dagger
 * injection; the `@Inject` fields are set directly.
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
