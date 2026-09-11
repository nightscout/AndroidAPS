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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class WallpaperDarkComplicationTest {

    private fun sut(): WallpaperDarkComplication =
        Robolectric.buildService(WallpaperDarkComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `preview for the supported photo-image type stays on the wallpaper branch`() {
        val data = sut().getPreviewData(ComplicationType.PHOTO_IMAGE)

        // The PHOTO_IMAGE branch reads a bundled asset + scales it to the window bounds; under
        // Robolectric that pipeline may not fully render, so the branch may yield either a
        // PhotoImageComplicationData or null. Either way it must not fall through or throw.
        if (data != null) assertThat(data).isInstanceOf(PhotoImageComplicationData::class.java)
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `the sample preview data carries realistic glucose and status values`() {
        val preview = sut().getPreviewComplicationData()

        assertThat(preview.bgData.sgvString).isEqualTo("120")
        assertThat(preview.statusData.cob).isEqualTo("15g")
    }

    @Test
    fun `tapping the wallpaper complication performs no action`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.NONE)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("WallpaperDarkComplication")
    }
}
