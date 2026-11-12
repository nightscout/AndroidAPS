package app.aaps.plugins.automation.triggers

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputBg
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

/**
 * A new glucose or temp target condition starts at the lowest threshold the editor offers, not at 0.0.
 * The editor shows 0.0 as an empty field, and a condition saved untouched compared against 0.
 */
class NewGlucoseConditionTest : TriggerTestBase() {

    @Test
    fun `a new condition starts at the lowest threshold of the profile units`() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        assertThat(TriggerBg(triggerDeps).bg.value).isEqualTo(InputBg.MGDL_MIN)
        assertThat(TriggerTempTargetValue(triggerDeps).ttValue.value).isEqualTo(InputBg.MGDL_MIN)

        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MMOL)
        assertThat(TriggerBg(triggerDeps).bg.value).isEqualTo(InputBg.MMOL_MIN)
        assertThat(TriggerTempTargetValue(triggerDeps).ttValue.value).isEqualTo(InputBg.MMOL_MIN)
    }

    @Test
    fun `an untouched new above condition does not fire for a low glucose`() = runTest {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(
            mutableListOf(InMemoryGlucoseValue(value = 40.0, timestamp = now - 1, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        )

        // Before, "above" an untouched field compared 40 mg/dL against 0 and fired.
        assertThat(TriggerBg(triggerDeps).comparator(Comparator.Compare.IS_GREATER).shouldRun()).isFalse()
    }

    @Test
    fun `a stored threshold is still read as stored`() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        val restored = TriggerBg(triggerDeps).fromJSON("""{"bg":0,"comparator":"IS_GREATER","units":"mg/dl"}""") as TriggerBg
        assertThat(restored.bg.value).isEqualTo(0.0)
    }
}
