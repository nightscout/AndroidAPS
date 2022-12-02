package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerProfilePercentTest : TriggerTestBase() {

    private val now = 1514766900000L

    @BeforeEach fun mock() {
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {
        var t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerProfilePercent
        Assert.assertEquals(213.0, t1.pct.value, 0.01)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private val bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"percentage\":110},\"type\":\"TriggerProfilePercent\"}"
    @Test fun toJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(bgJson, t.toJSON())
    }

    @Test fun fromJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(120.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerProfilePercent
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(120.0, t2.pct.value, 0.01)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(info.nightscout.interfaces.R.drawable.ic_actions_profileswitch), TriggerProfilePercent(injector).icon())
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.profilepercentage, TriggerProfilePercent(injector).friendlyName()) // not mocked
    }
}