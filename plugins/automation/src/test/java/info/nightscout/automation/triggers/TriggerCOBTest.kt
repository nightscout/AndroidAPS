package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.elements.Comparator
import info.nightscout.interfaces.iob.CobInfo
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`

class TriggerCOBTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        `when`(sp.getInt(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(48)
    }

    @Test fun shouldRunTest() {
        // COB value is 6
        `when`(iobCobCalculator.getCobInfo("AutomationTriggerCOB")).thenReturn(CobInfo(0, 6.0, 2.0))
        var t: TriggerCOB = TriggerCOB(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerCOB(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(6.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerCOB(injector).setValue(10.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerCOB(injector).setValue(5.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertEquals(213.0, t.cob.value, 0.01)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"carbs\":4},\"type\":\"TriggerCOB\"}"
    @Test fun toJSONTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(bgJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerCOB = TriggerCOB(injector).setValue(4.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerCOB
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(4.0, t2.cob.value, 0.01)
    }

    @Test fun iconTest() {
        Assertions.assertEquals(Optional.of(info.nightscout.core.main.R.drawable.ic_cp_bolus_carbs), TriggerCOB(injector).icon())
    }
}