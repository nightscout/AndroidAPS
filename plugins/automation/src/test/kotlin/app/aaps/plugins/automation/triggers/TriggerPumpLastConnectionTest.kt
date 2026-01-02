package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerPumpLastConnectionTest : TriggerTestBase() {

    @Test
    fun shouldRunTest() {
//        System.currentTimeMillis() is always 0
//        and so is every last connection time
        assertThat(testPumpPlugin.lastDataTime).isEqualTo(0L)
        whenever(dateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerPumpLastConnection(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.minutesAgo.value).isEqualTo(110)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerPumpLastConnection(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.minutesAgo.value).isEqualTo(10)
        assertThat(t.shouldRun()).isFalse() // 0 == 10 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue() // 5 => 0 -> TRUE
        t = TriggerPumpLastConnection(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isFalse() // 310 <= 0 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse() // 420 == 0 -> FALSE
    }

    @Test fun copyConstructorTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerPumpLastConnection
        assertThat(t1.minutesAgo.value).isEqualTo(213)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"TriggerPumpLastConnection\"}"
    @Test fun toJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(lbJson, t.toJSON(), true)
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerPumpLastConnection
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.minutesAgo.value).isEqualTo(410)
    }

    @Test fun iconTest() {
        assertThat(TriggerPumpLastConnection(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_remove)
    }

    @Test fun friendlyNameTest() {
        assertThat(TriggerPumpLastConnection(injector).friendlyName()).isEqualTo(R.string.automation_trigger_pump_last_connection_label)
    }
}
