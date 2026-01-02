package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerTempTargetValueTest : TriggerTestBase() {

    @BeforeEach
    fun prepare() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun shouldRunTest() {
        whenever(persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())).thenReturn(
            TT(
                duration = 60000,
                highTarget = 140.0,
                lowTarget = 140.0,
                reason = TT.Reason.CUSTOM,
                timestamp = now - 1
            )
        )

        var t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(141.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(141.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
        assertThat(t.shouldRun()).isFalse()
        t = TriggerTempTargetValue(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        assertThat(t.shouldRun()).isFalse()
        whenever(persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())).thenReturn(null)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerTempTargetValue
        assertThat(t1.ttValue.value).isWithin(0.01).of(140.0)
        assertThat(t1.ttValue.units).isEqualTo(GlucoseUnit.MGDL)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var ttJson = "{\"data\":{\"tt\":7.7,\"comparator\":\"IS_EQUAL\",\"units\":\"mmol\"},\"type\":\"TriggerTempTargetValue\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(ttJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTargetValue
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.ttValue.value).isWithin(0.01).of(7.7)
        assertThat(t2.ttValue.units).isEqualTo(GlucoseUnit.MMOL)
    }

    @Test
    fun iconTest() {
        assertThat(TriggerTempTargetValue(injector).icon().get()).isEqualTo(R.drawable.ic_keyboard_tab)
    }
}

