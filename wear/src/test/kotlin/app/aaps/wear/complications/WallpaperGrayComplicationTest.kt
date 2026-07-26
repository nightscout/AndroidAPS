package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [WallpaperGrayComplication] and the shared wallpaper logic it inherits from
 * [WallpaperComplication] / [ModernBaseComplicationProviderService]: the PHOTO_IMAGE branch that
 * loads the `watch_gray.jpg` asset into a [PhotoImageComplicationData], the else→null fallback and
 * the action/name accessors. Built via [Robolectric] so a Context (with assets) is attached without
 * running onCreate's Dagger injection; the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WallpaperGrayComplicationTest {

    private fun sut(): WallpaperGrayComplication =
        Robolectric.buildService(WallpaperGrayComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `preview builds a photo-image complication from the gray wallpaper asset`() {
        val data = sut().getPreviewData(ComplicationType.PHOTO_IMAGE)

        assertThat(data).isInstanceOf(PhotoImageComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `tapping the wallpaper complication performs no action`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.NONE)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("WallpaperGrayComplication")
    }
}
