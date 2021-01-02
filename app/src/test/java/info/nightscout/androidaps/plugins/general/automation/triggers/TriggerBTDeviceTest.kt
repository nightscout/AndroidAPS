package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorConnect
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class TriggerBTDeviceTest : TriggerTestBase() {

    var now = 1514766900000L
    var someName = "Headset"
    var btJson = "{\"data\":{\"comparator\":\"ON_CONNECT\",\"name\":\"Headset\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBTDevice\"}"

    @Test fun shouldRun() {
        val t: TriggerBTDevice = TriggerBTDevice(injector)
    }

    @Test fun toJSON() {
        val t: TriggerBTDevice = TriggerBTDevice(injector)
        t.btDevice.value = someName
        Assert.assertEquals(btJson, t.toJSON())
    }

    @Test
    fun fromJSON() {
        val t2 = TriggerDummy(injector).instantiate(JSONObject(btJson)) as TriggerBTDevice
        Assert.assertEquals(ComparatorConnect.Compare.ON_CONNECT, t2.comparator.value)
        Assert.assertEquals("Headset", t2.btDevice.value)
    }

    @Test
    fun icon() {
        Assert.assertEquals(Optional.of(R.drawable.ic_bluetooth_white_48dp), TriggerBTDevice(injector).icon())
    }

    @Test fun duplicate() {
        val t: TriggerBTDevice = TriggerBTDevice(injector).also {
            it.comparator.value = ComparatorConnect.Compare.ON_DISCONNECT
            it.btDevice.value = someName
        }
        val t1 = t.duplicate() as TriggerBTDevice
        Assert.assertEquals("Headset", t1.btDevice.value)
        Assert.assertEquals(ComparatorConnect.Compare.ON_DISCONNECT, t.comparator.value)
    }
}