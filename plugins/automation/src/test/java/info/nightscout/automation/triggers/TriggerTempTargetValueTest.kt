package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.interfaces.GlucoseUnit
import io.reactivex.rxjava3.core.Single
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerTempTargetValueTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(dateUtil.now()).thenReturn(now)
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
        Assert.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(141.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(141.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(139.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
        Assert.assertFalse(t.shouldRun())
        t = TriggerTempTargetValue(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertFalse(t.shouldRun())
        `when`(repository.getTemporaryTargetActiveAt(dateUtil.now())).thenReturn(Single.just(ValueWrapper.Absent()))
        Assert.assertTrue(t.shouldRun())
    }

    @Test
    fun copyConstructorTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MGDL).setValue(140.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerTempTargetValue
        Assert.assertEquals(140.0, t1.ttValue.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MGDL, t1.ttValue.units)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var ttJson = "{\"data\":{\"tt\":7.7,\"comparator\":\"IS_EQUAL\",\"units\":\"mmol\"},\"type\":\"TriggerTempTargetValue\"}"

    @Test
    fun toJSONTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(ttJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTargetValue = TriggerTempTargetValue(injector).setUnits(GlucoseUnit.MMOL).setValue(7.7).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTargetValue
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(7.7, t2.ttValue.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MMOL, t2.ttValue.units)
    }

    @Test
    fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_keyboard_tab), TriggerTempTargetValue(injector).icon())
    }
}

