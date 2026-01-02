package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.BS
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerBolusAgoTest : TriggerTestBase() {

    @Test
    fun shouldRunTest() {
        // Set last bolus time to now
        whenever(persistenceLayer.getNewestBolusOfType(BS.Type.NORMAL)).thenReturn(
            BS(
                timestamp = now,
                amount = 0.0,
                type = BS.Type.NORMAL
            )
        )
        whenever(dateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerBolusAgo(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.minutesAgo.value).isEqualTo(110)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBolusAgo(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.minutesAgo.value).isEqualTo(10)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBolusAgo(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBolusAgo(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBolusAgo(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isFalse()
        t = TriggerBolusAgo(injector).setValue(2).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        assertThat(t.shouldRun()).isTrue()
        // Set last bolus time to 0
        whenever(persistenceLayer.getNewestBolusOfType(BS.Type.NORMAL)).thenReturn(
            BS(
                timestamp = 0,
                amount = 0.0,
                type = BS.Type.NORMAL
            )
        )
        t = TriggerBolusAgo(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBolusAgo
        assertThat(t1.minutesAgo.value).isEqualTo(213)
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"TriggerBolusAgo\"}"
    @Test fun toJSONTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(lbJson, t.toJSON(), true)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBolusAgo
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.minutesAgo.value).isEqualTo(410)
    }

    @Test fun iconTest() {
        assertThat(TriggerBolusAgo(injector).icon().get()).isEqualTo(app.aaps.core.objects.R.drawable.ic_bolus)
    }
}
