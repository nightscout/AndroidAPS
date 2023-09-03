package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.interfaces.iob.IobTotal
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`

class TriggerIobTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
    }

    @Test fun shouldRunTest() {
        `when`(iobCobCalculator.calculateFromTreatmentsAndTemps(ArgumentMatchers.anyLong(), anyObject())).thenReturn(generateIobRecordData())
        var t: TriggerIob = TriggerIob(injector).setValue(1.1).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerIob(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerIob(injector).setValue(0.8).comparator(Comparator.Compare.IS_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerIob(injector).setValue(0.8).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerIob(injector).setValue(0.9).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerIob(injector).setValue(1.2).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerIob(injector).setValue(1.1).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerIob(injector).setValue(1.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerIob(injector).setValue(0.9).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerIob = TriggerIob(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertEquals(213.0, t.insulin.value, 0.01)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"insulin\":4.1},\"type\":\"TriggerIob\"}"
    @Test fun toJSONTest() {
        val t: TriggerIob = TriggerIob(injector).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(bgJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerIob = TriggerIob(injector).setValue(4.1).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerIob
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(4.1, t2.insulin.value, 0.01)
    }

    @Test fun iconTest() {
        Assertions.assertEquals(Optional.of(R.drawable.ic_keyboard_capslock), TriggerIob(injector).icon())
    }

    private fun generateIobRecordData(): IobTotal {
        val iobTotal = IobTotal(1)
        iobTotal.iob = 1.0
        return iobTotal
    }
}