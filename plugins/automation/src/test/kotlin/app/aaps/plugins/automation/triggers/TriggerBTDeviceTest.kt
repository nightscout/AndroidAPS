package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.elements.ComparatorConnect
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

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
        JSONAssert.assertEquals(btJson, t.toJSON(), true)
    }

    @Test
    fun fromJSON() {
        val t2 = TriggerDummy(injector).instantiate(JSONObject(btJson)) as TriggerBTDevice
        assertThat(t2.comparator.value).isEqualTo(ComparatorConnect.Compare.ON_CONNECT)
        assertThat(t2.btDevice.value).isEqualTo("Headset")
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
        assertThat(t1.btDevice.value).isEqualTo("Headset")
        assertThat(t.comparator.value).isEqualTo(ComparatorConnect.Compare.ON_DISCONNECT)
    }
}
