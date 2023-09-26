package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerProfilePercentTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
    }

    @Test fun shouldRunTest() {
        var t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertTrue(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assertions.assertFalse(t.shouldRun())
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assertions.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerProfilePercent
        Assertions.assertEquals(213.0, t1.pct.value, 0.01)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private val bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"percentage\":110},\"type\":\"TriggerProfilePercent\"}"
    @Test fun toJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(bgJson, t.toJSON())
    }

    @Test fun fromJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(120.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerProfilePercent
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals(120.0, t2.pct.value, 0.01)
    }

    @Test fun iconTest() {
        assertThat(TriggerProfilePercent(injector).icon().get()).isEqualTo(app.aaps.core.ui.R.drawable.ic_actions_profileswitch)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(R.string.profilepercentage, TriggerProfilePercent(injector).friendlyName()) // not mocked
    }
}