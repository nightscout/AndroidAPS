package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class ReceiverDelegateTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var fabricPrivacy: FabricPrivacy

    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    private lateinit var sut: ReceiverDelegate

    @BeforeEach
    fun prepare() {
        //receiverStatusStore = ReceiverStatusStore(context, rxBus)
        sut = ReceiverDelegate(rxBus, rh, preferences, receiverStatusStore, aapsSchedulers, fabricPrivacy)
    }

    @Test
    fun testCalculateStatusChargingState() {
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(false)
        assertThat(sut.calculateStatus(EventChargingState(false, 0))).isTrue()
        assertThat(sut.calculateStatus(EventChargingState(true, 0))).isFalse()
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(true)
        assertThat(sut.calculateStatus(EventChargingState(true, 0))).isTrue()
        assertThat(sut.calculateStatus(EventChargingState(false, 0))).isFalse()
    }

    @Test
    fun testCalculateStatusNetworkState() {
        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange())).isFalse()

        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("test 1")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isFalse()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "test 1", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(ssid = "test 1", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange())).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange())).isFalse()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true))).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(false)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true))).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true))).isTrue()
    }
}
