package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.interfaces.GlucoseUnit
import io.reactivex.rxjava3.core.Single
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerTempTargetValueTest : TriggerTestBase() {

    @BeforeEach
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun shouldRunTest() {
        `when`(repository.getTemporaryTargetActiveAt(dateUtil.now())).thenReturn(
            Single.just(
                ValueWrapper.Existing(
                    TemporaryTarget(
                        duration = 60000,
                        highTarget = 140.0,
                        lowTarget = 140.0,
                        reason = TemporaryTarget.Reason.CUSTOM,
                        timestamp = now - 1
                    )
                )
            )
        )
        var t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(141.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(141.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
        Assertions.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assertions.assertFalse(t.shouldRun())
        `when`(repository.getTemporaryTargetActiveAt(dateUtil.now())).thenReturn(Single.just(ValueWrapper.Absent()))
        Assertions.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerTempTargetValue
        Assertions.assertEquals(140.0, t1.ttValue.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MGDL, t1.ttValue.units)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var ttJson = "{\"data\":{\"tt\":7.7,\"comparator\":\"IS_EQUAL\",\"units\":\"mmol\"},\"type\":\"TriggerTempTargetValue\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(ttJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTargetValue
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(7.7, t2.ttValue.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MMOL, t2.ttValue.units)
    }

    @Test
    fun iconTest() {
        Assertions.assertEquals(Optional.of(R.drawable.ic_keyboard_tab), TriggerTempTargetValue(injector).icon())
    }
}

