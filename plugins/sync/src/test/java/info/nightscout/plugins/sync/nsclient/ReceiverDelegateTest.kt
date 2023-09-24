package info.nightscout.plugins.sync.nsclient

import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import info.nightscout.plugins.sync.R
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ReceiverDelegateTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var fabricPrivacy: FabricPrivacy

    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    private lateinit var sut: ReceiverDelegate

    @BeforeEach
    fun prepare() {
        //receiverStatusStore = ReceiverStatusStore(context, rxBus)
        sut = ReceiverDelegate(rxBus, rh, sp, receiverStatusStore, aapsSchedulers, fabricPrivacy)
    }

    @Test
    fun testCalculateStatusChargingState() {
        `when`(sp.getBoolean(R.string.key_ns_battery, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_charging, true)).thenReturn(false)
        assertThat(sut.calculateStatus(EventChargingState(false, 0))).isTrue()
        assertThat(sut.calculateStatus(EventChargingState(true, 0))).isFalse()
        `when`(sp.getBoolean(R.string.key_ns_battery, true)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_charging, true)).thenReturn(true)
        assertThat(sut.calculateStatus(EventChargingState(true, 0))).isTrue()
        assertThat(sut.calculateStatus(EventChargingState(false, 0))).isFalse()
    }

    @Test
    fun testCalculateStatusNetworkState() {
        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange())).isFalse()

        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("test 1")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isFalse()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "test 1", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "test 1", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange())).isFalse()

        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange())).isFalse()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true))).isFalse()

        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(false)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true))).isFalse()

        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true))).isTrue()
    }
}
