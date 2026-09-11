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

/** Dataset-2 (AAPSClient2 follower) SGV complication — same shape as [SgvComplication] but reads bgData2. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class SgvComplicationExt2Test {

    private fun sut(): SgvComplicationExt2 =
        Robolectric.buildService(SgvComplicationExt2::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    @Test
    fun `preview builds a short-text complication`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isInstanceOf(ShortTextComplicationData::class.java)
    }

    @Test
    fun `an unsupported type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.RANGED_VALUE)).isNull()
    }

    @Test
    fun `defaults to the menu tap action and identifies itself`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.MENU)
        assertThat(sut().getProviderCanonicalName()).contains("SgvComplicationExt2")
    }
}
