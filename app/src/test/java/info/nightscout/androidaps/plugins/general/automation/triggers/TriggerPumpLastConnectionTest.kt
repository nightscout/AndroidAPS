package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class, VirtualPumpPlugin::class)
class TriggerPumpLastConnectionTest : TriggerTestBase() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    var now = 1514766900000L

    @Before
    fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
    }

    @Test
    fun shouldRunTest() {
//        System.currentTimeMillis() is always 0
//        and so is every last connection time
        PowerMockito.`when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        Assert.assertEquals(0L, virtualPumpPlugin.lastDataTime())
        PowerMockito.`when`(DateUtil.now()).thenReturn(now + 10 * 60 * 1000) // set current time to now + 10 min
        var t = TriggerPumpLastConnection(injector).setValue(110).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(110, t.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t.comparator.value)
        Assert.assertFalse(t.shouldRun())
        t = TriggerPumpLastConnection(injector).setValue(10).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(10, t.minutesAgo.value)
        Assert.assertFalse(t.shouldRun()) // 0 == 10 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(5).comparator(Comparator.Compare.IS_EQUAL_OR_GREATER)
        Assert.assertTrue(t.shouldRun()) // 5 => 0 -> TRUE
        t = TriggerPumpLastConnection(injector).setValue(310).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        Assert.assertFalse(t.shouldRun()) // 310 <= 0 -> FALSE
        t = TriggerPumpLastConnection(injector).setValue(420).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertFalse(t.shouldRun()) // 420 == 0 -> FALSE
    }

    @Test fun copyConstructorTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(213).comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerPumpLastConnection
        Assert.assertEquals(213, t1.minutesAgo.value)
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    private var lbJson = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"minutesAgo\":410},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerPumpLastConnection\"}"
    @Test fun toJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        Assert.assertEquals(lbJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerPumpLastConnection = TriggerPumpLastConnection(injector).setValue(410).comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerPumpLastConnection
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assert.assertEquals(410, t2.minutesAgo.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_remove), TriggerPumpLastConnection(injector).icon())
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.automation_trigger_pump_last_connection_label.toLong(), TriggerPumpLastConnection(injector).friendlyName().toLong())
    }
}