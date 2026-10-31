package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
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
        // ReceiverDelegate.init launches preferences.observe(key).drop(1)…launchIn(ioScope) for each of these
        // keys. An unstubbed observe() returns null (mock default), so .drop(1) NPEs on the IO scope; that
        // uncaught exception escapes this test and surfaces as "UncaughtExceptionsBeforeTest" in an unrelated
        // later test in the same fork. Stub every observed key so the drop operates on a real flow.
        whenever(preferences.observe(BooleanKey.NsClientUseWifi)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.NsClientUseCellular)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(StringKey.NsClientWifiSsids)).thenReturn(MutableStateFlow(""))
        whenever(preferences.observe(BooleanKey.NsClientUseRoaming)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.NsClientUseOnCharging)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.NsClientUseOnBattery)).thenReturn(MutableStateFlow(false))
        sut = ReceiverDelegate(aapsLogger, rh, preferences, receiverStatusStore)
    }

    @Test
    fun testCalculateStatusChargingState() {
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(false)
        assertThat(sut.calculateStatus(ReceiverStatusStore.ChargingStatus(false, 0))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.ChargingStatus(true, 0))).isFalse()
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(true)
        assertThat(sut.calculateStatus(ReceiverStatusStore.ChargingStatus(true, 0))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.ChargingStatus(false, 0))).isFalse()
    }

    /**
     * The allowed-network list where no network name can be read.
     *
     * Off Android nothing can produce one - iOS wants the Access WiFi Information entitlement, a
     * desktop `NetworkInterface` has no name - so `contains(ssid)` could never match and a user who
     * filled the field in lost Nightscout sync on every network, silently. The list is skipped there
     * instead.
     *
     * The pair below is the whole point: same empty name, opposite answers, because on Android an
     * empty name means the read failed this time and the user's filter must still be obeyed.
     */
    @Test
    fun `a platform that cannot read the network name ignores the allowed list`() {
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("home;work")

        val cannotRead = ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssid = "", ssidReadable = false)
        assertThat(sut.calculateStatus(cannotRead)).isTrue()

        val readFailedThisTime = ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssid = "", ssidReadable = true)
        assertThat(sut.calculateStatus(readFailedThisTime)).isFalse()
    }

    /** Skipping the filter must not turn wifi on when the user switched wifi off outright. */
    @Test
    fun `an unreadable network name does not override the wifi switch`() {
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(false)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("home")

        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssidReadable = false))).isFalse()
    }

    /** Where the name is readable, the list still decides - the Android path is untouched. */
    @Test
    fun `a readable network name is still matched against the list`() {
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("home;work")

        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssid = "home"))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssid = "cafe"))).isFalse()
    }

    @Test
    fun testCalculateStatusNetworkState() {
        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus())).isFalse()

        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("test 1")
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, wifiConnected = false, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true))).isFalse()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(ssid = "test 1", mobileConnected = true, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(ssid = "test 1", mobileConnected = false, wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus())).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus())).isFalse()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true))).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(false)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, roaming = true))).isFalse()

        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, roaming = false))).isTrue()
        assertThat(sut.calculateStatus(ReceiverStatusStore.NetworkStatus(mobileConnected = true, roaming = true))).isTrue()
    }
}