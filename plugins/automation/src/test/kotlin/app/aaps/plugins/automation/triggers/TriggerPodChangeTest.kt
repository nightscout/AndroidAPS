package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerPodChangeTest : TriggerTestBase() {

    @Test fun shouldRunTest() = runTest {
        // Cannula change is "now"
        val cannulaChangeEvent = TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now, type = TE.Type.CANNULA_CHANGE)
        // Test settings export was 1 minute before or after Cannula change
        val settingsExportEventIsBefore =
            TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now - T.mins(1).msecs(), type = TE.Type.SETTINGS_EXPORT)
        val settingsExportEventIsAfter =
            TE(glucoseUnit = GlucoseUnit.MGDL, timestamp = now + T.mins(1).msecs(), type = TE.Type.SETTINGS_EXPORT)
        val t = TriggerPodChange(injector)

        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(cannulaChangeEvent)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SETTINGS_EXPORT)).thenReturn(settingsExportEventIsBefore)
        assertThat(t.shouldRun()).isTrue()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(cannulaChangeEvent)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SETTINGS_EXPORT)).thenReturn(settingsExportEventIsAfter)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun shouldRunNotAvailable() = runTest {
        val t = TriggerPodChange(injector)
        // No cannula change and no export events
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(null)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SETTINGS_EXPORT)).thenReturn(null)
        assertThat(t.shouldRun()).isFalse()

        // No cannula change events
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(null)
        assertThat(t.shouldRun()).isFalse()

        // No settings export events
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SETTINGS_EXPORT)).thenReturn(null)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun toJSONTest() {
        // JSON not relevant...
        val podChangeJson = "{}"
        val t = TriggerPodChange(injector)
        JSONAssert.assertEquals(podChangeJson, t.dataJSON().toString(), true)
    }

    @Test fun fromJSONTest() {
        // JSON not relevant...
        val podChangeJson = "{}"
        val t = TriggerPodChange(injector)
        JSONAssert.assertEquals(podChangeJson, t.fromJSON("").dataJSON(), true)
    }
}
