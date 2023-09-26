package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.elements.ComparatorConnect
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TriggerBTDeviceTest : TriggerTestBase() {

    private var someName = "Headset"
    private var btJson = "{\"data\":{\"comparator\":\"ON_CONNECT\",\"name\":\"Headset\"},\"type\":\"TriggerBTDevice\"}"

    @Test fun shouldRun() {
        @Suppress("UNUSED_VARIABLE")
        val t = TriggerBTDevice(injector)
    }

    @Test fun toJSON() {
        val t = TriggerBTDevice(injector)
        t.btDevice.value = someName
        Assertions.assertEquals(btJson, t.toJSON())
    }

    @Test
    fun fromJSON() {
        val t2 = TriggerDummy(injector).instantiate(JSONObject(btJson)) as TriggerBTDevice
        Assertions.assertEquals(ComparatorConnect.Compare.ON_CONNECT, t2.comparator.value)
        Assertions.assertEquals("Headset", t2.btDevice.value)
    }

    @Test
    fun icon() {
        assertThat(TriggerBTDevice(injector).icon().get()).isEqualTo(app.aaps.core.ui.R.drawable.ic_bluetooth_white_48dp)
    }

    @Test fun duplicate() {
        val t: TriggerBTDevice = TriggerBTDevice(injector).also {
            it.comparator.value = ComparatorConnect.Compare.ON_DISCONNECT
            it.btDevice.value = someName
        }
        val t1 = t.duplicate() as TriggerBTDevice
        Assertions.assertEquals("Headset", t1.btDevice.value)
        Assertions.assertEquals(ComparatorConnect.Compare.ON_DISCONNECT, t.comparator.value)
    }
}