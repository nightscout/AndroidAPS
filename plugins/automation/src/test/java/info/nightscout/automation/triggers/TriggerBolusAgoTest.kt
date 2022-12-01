package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.elements.Comparator
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import io.reactivex.rxjava3.core.Single
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerBolusAgoTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach
    fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun shouldRunTest() {
        // Set last bolus time to now
        `when`(repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL)).thenReturn(
            Single.just(
                ValueWrapper.Existing(
                    Bolus(
                        timestamp = now,
                        amount = 0.0,
                        type = Bolus.Type.NORMAL
                    )
                )
            )
        )
        `when`(dateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerBolusAgo(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(110, t.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(10, t.minutesAgo.value)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertFalse(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(2).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun())
        t = TriggerBolusAgo(injector).setValue(390).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertTrue(t.shouldRun())
        // Set last bolus time to 0
        `when`(repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL)).thenReturn(
            Single.just(
                ValueWrapper.Existing(
                    Bolus(
                        timestamp = 0,
                        amount = 0.0,
                        type = Bolus.Type.NORMAL
                    )
                )
            )
        )
        t = TriggerBolusAgo(injector).comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerBolusAgo
        Assert.assertEquals(213, t1.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"TriggerBolusAgo\"}"
    @Test fun toJSONTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(lbJson, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerBolusAgo = TriggerBolusAgo(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerBolusAgo
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(410, t2.minutesAgo.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(info.nightscout.core.main.R.drawable.ic_bolus), TriggerBolusAgo(injector).icon())
    }
}