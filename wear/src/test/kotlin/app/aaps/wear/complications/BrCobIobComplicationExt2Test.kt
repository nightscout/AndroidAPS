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

/** Dataset-2 (AAPSClient2 follower) Basal/COB/IOB complication — reads statusData2, uses DisplayFormat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class BrCobIobComplicationExt2Test {

    private fun sut(): BrCobIobComplicationExt2 =
        Robolectric.buildService(BrCobIobComplicationExt2::class.java).get().also {
            it.aapsLogger = AAPSLoggerTest()
            it.displayFormat = DisplayFormat().also { d -> d.sp = mock(); d.context = it }
        }

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
        assertThat(sut().getProviderCanonicalName()).contains("BrCobIobComplicationExt2")
    }
}
