package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.elements.ComparatorConnect
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class TriggerBTDeviceTest : TriggerTestBase() {

    var now = 1514766900000L
    private var someName = "Headset"
    private var btJson = "{\"data\":{\"comparator\":\"ON_CONNECT\",\"name\":\"Headset\"},\"type\":\"TriggerBTDevice\"}"

    @Test fun shouldRun() {
        @Suppress("UNUSED_VARIABLE")
        val t = TriggerBTDevice(injector)
    }

    @Test fun toJSON() {
        val t = TriggerBTDevice(injector)
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
        Assert.assertEquals(Optional.of(info.nightscout.core.ui.R.drawable.ic_bluetooth_white_48dp), TriggerBTDevice(injector).icon())
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