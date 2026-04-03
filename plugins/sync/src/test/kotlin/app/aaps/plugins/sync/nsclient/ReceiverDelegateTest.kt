package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class ReceiverDelegateTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock private lateinit var receiverStatusStore: ReceiverStatusStore
    private lateinit var sut: ReceiverDelegate

    @BeforeEach
    fun prepare() {
        whenever(receiverStatusStore.networkStatusFlow).thenReturn(MutableStateFlow(null))
        whenever(receiverStatusStore.chargingStatusFlow).thenReturn(MutableStateFlow(null))
        sut = ReceiverDelegate(rh, preferences, receiverStatusStore)
    }

    @Test
    fun testCalculateStatusChargingState() {
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(false)
        assertThat(sut.calculateStatus(ChargingStatus(false, 0))).isTrue()
        assertThat(sut.calculateStatus(ChargingStatus(true, 0))).isFalse()
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(true)
        assertThat(sut.calculateStatus(ChargingStatus(true, 0))).isTrue()
        assertThat(sut.calculateStatus(ChargingStatus(false, 0))).isFalse()
    }

    @Test
    fun testCalculateStatusNetworkState() {
        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus())).isFalse()

        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("test 1")
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isFalse()
        assertThat(sut.calculateStatus(NetworkStatus(ssid = "test 1", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(ssid = "test 1", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus())).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(NetworkStatus(wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus())).isFalse()
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true))).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(false)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, roaming = true))).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(NetworkStatus(mobileConnected = true, roaming = true))).isTrue()
    }
}
