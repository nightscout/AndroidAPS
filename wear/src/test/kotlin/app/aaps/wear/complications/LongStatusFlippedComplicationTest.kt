package app.aaps.wear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
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
 * Covers [LongStatusFlippedComplication] and the shared [ModernBaseComplicationProviderService]
 * logic it inherits: [getPreviewData]/[getPreviewComplicationData] (sample data + tap intent →
 * build via [DisplayFormat]), the unsupported-type null branch and the action/name accessors.
 * Built via [Robolectric] so a Context is attached without running onCreate's Dagger injection;
 * the `@Inject` fields are set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class LongStatusFlippedComplicationTest {

    private fun sut(): LongStatusFlippedComplication =
        Robolectric.buildService(LongStatusFlippedComplication::class.java).get().also {
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
        // Reading age must auto-update via the system, not be baked in as static text
        // (issue #3821 — no app wake-ups needed to keep it current)
        assertThat((data as LongTextComplicationData).text)
            .isInstanceOf(TimeDifferenceComplicationText::class.java)
        // Everything is in the text field — a title would make watch faces join the two
        // fields with their own separator (often "/")
        assertThat(data.title).isNull()
    }

    @Test
    fun `an unsupported complication type yields null`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
    }

    @Test
    fun `tapping the complication opens the menu by default`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.MENU)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("LongStatusFlippedComplication")
    }
}
