package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerProfilePercentTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
    }

    @Test fun shouldRunTest() {
        var t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerProfilePercent(injector).setValue(100.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerProfilePercent(injector).setValue(101.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerProfilePercent(injector).setValue(215.0).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerProfilePercent(injector).setValue(90.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(213.0).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerProfilePercent
        assertThat( t1.pct.value).isWithin(0.01).of(213.0)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private val bgJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"percentage\":110},\"type\":\"TriggerProfilePercent\"}"
    @Test fun toJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(110.0).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(bgJson, t.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        val t: TriggerProfilePercent = TriggerProfilePercent(injector).setValue(120.0).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerProfilePercent
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.pct.value).isWithin(0.01).of(120.0)
    }

    @Test fun iconTest() {
        assertThat(TriggerProfilePercent(injector).icon().get()).isEqualTo(app.aaps.core.ui.R.drawable.ic_actions_profileswitch)
    }

    @Test fun friendlyNameTest() {
        assertThat(TriggerProfilePercent(injector).friendlyName()).isEqualTo(R.string.profilepercentage) // not mocked
    }
}
