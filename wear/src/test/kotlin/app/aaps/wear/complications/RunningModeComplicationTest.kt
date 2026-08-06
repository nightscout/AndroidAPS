package app.aaps.wear.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Covers [RunningModeComplication]: the preview (a closed loop) builds for the supported types
 * (SMALL_IMAGE / MONOCHROMATIC_IMAGE), every running mode renders without error, text types are
 * rejected (image-only by design), and the action/name accessors resolve. Built via [Robolectric]
 * so a Context is attached without running onCreate's Dagger injection; the `@Inject` fields are
 * set directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class RunningModeComplicationTest {

    private fun sut(): RunningModeComplication =
        Robolectric.buildService(RunningModeComplication::class.java).get().also { it.aapsLogger = AAPSLoggerTest() }

    private fun dummyIntent(service: RunningModeComplication): PendingIntent =
        PendingIntent.getActivity(service, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    @Test
    fun `preview builds a small-image complication for the sample mode`() {
        assertThat(sut().getPreviewData(ComplicationType.SMALL_IMAGE)).isInstanceOf(SmallImageComplicationData::class.java)
    }

    @Test
    fun `preview builds a monochromatic-image complication for the sample mode`() {
        assertThat(sut().getPreviewData(ComplicationType.MONOCHROMATIC_IMAGE)).isInstanceOf(MonochromaticImageComplicationData::class.java)
    }

    @Test
    fun `preview shows a closed loop, not the UNKNOWN default`() {
        assertThat(sut().getPreviewComplicationData().statusData.loopMode).isEqualTo(LoopStatusData.LoopMode.CLOSED)
    }

    @Test
    fun `every running mode builds a small-image complication`() {
        val complication = sut()
        val pendingIntent = dummyIntent(complication)
        for (mode in LoopStatusData.LoopMode.entries) {
            val store = complication.getPreviewComplicationData().let { it.copy(statusData = it.statusData.copy(loopMode = mode)) }
            assertThat(complication.buildComplicationData(ComplicationType.SMALL_IMAGE, store, pendingIntent))
                .isInstanceOf(SmallImageComplicationData::class.java)
        }
    }

    @Test
    fun `no sync from the phone renders as UNKNOWN, not the last known mode`() {
        val complication = sut()
        val pendingIntent = dummyIntent(complication)
        val store = complication.getPreviewComplicationData()
        val noSync = complication.buildNoSyncComplicationData(ComplicationType.SMALL_IMAGE, store, pendingIntent) as SmallImageComplicationData
        val fresh = complication.buildComplicationData(ComplicationType.SMALL_IMAGE, store, pendingIntent) as SmallImageComplicationData
        val now = Instant.now()
        assertThat(noSync.contentDescription!!.getTextAt(complication.resources, now).toString())
            .isNotEqualTo(fresh.contentDescription!!.getTextAt(complication.resources, now).toString())
    }

    @Test
    fun `text complication types are rejected - this complication is image-only`() {
        assertThat(sut().getPreviewData(ComplicationType.SHORT_TEXT)).isNull()
        assertThat(sut().getPreviewData(ComplicationType.LONG_TEXT)).isNull()
    }

    @Test
    fun `tapping the running mode complication opens the mode picker`() {
        assertThat(sut().getComplicationAction()).isEqualTo(ComplicationAction.RUNNING_MODE)
    }

    @Test
    fun `the provider canonical name identifies this complication`() {
        assertThat(sut().getProviderCanonicalName()).contains("RunningModeComplication")
    }
}
