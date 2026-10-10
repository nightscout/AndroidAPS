package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [TidepoolConnectivitySettings] (#2993): Tidepool follows the Nightscout connection settings
 * while the switch is on, and its own settings when it is off.
 */
class TidepoolConnectivitySettingsTest {

    private val preferences: Preferences = mock()
    private val tidepoolCellular = MutableStateFlow(true)

    @BeforeEach
    fun setUp() {
        // Every key is observed when the settings are built
        listOf(
            BooleanKey.NsClientUseWifi, BooleanKey.NsClientUseCellular, BooleanKey.NsClientUseRoaming,
            BooleanKey.NsClientUseOnBattery, BooleanKey.NsClientUseOnCharging,
            TidepoolBooleanKey.UseNsConnectionSettings, TidepoolBooleanKey.UseRoaming, TidepoolBooleanKey.UseWifi,
            TidepoolBooleanKey.UseOnBattery, TidepoolBooleanKey.UseOnCharging
        ).forEach { whenever(preferences.observe(it)).thenReturn(MutableStateFlow(true)) }
        whenever(preferences.observe(TidepoolBooleanKey.UseCellular)).thenReturn(tidepoolCellular)
        whenever(preferences.observe(StringKey.NsClientWifiSsids)).thenReturn(MutableStateFlow(""))
        whenever(preferences.observe(TidepoolStringKey.WifiSsids)).thenReturn(MutableStateFlow(""))

        // Nightscout: cellular and battery allowed. Tidepool's own: only WiFi while charging, at "home".
        whenever(preferences.get(BooleanKey.NsClientUseCellular)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseRoaming)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseWifi)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientWifiSsids)).thenReturn("")
        whenever(preferences.get(BooleanKey.NsClientUseOnBattery)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientUseOnCharging)).thenReturn(true)
        whenever(preferences.get(TidepoolBooleanKey.UseCellular)).thenReturn(false)
        whenever(preferences.get(TidepoolBooleanKey.UseRoaming)).thenReturn(false)
        whenever(preferences.get(TidepoolBooleanKey.UseWifi)).thenReturn(true)
        whenever(preferences.get(TidepoolStringKey.WifiSsids)).thenReturn("home")
        whenever(preferences.get(TidepoolBooleanKey.UseOnBattery)).thenReturn(false)
        whenever(preferences.get(TidepoolBooleanKey.UseOnCharging)).thenReturn(true)
    }

    private fun followNightscout(follow: Boolean) =
        whenever(preferences.get(TidepoolBooleanKey.UseNsConnectionSettings)).thenReturn(follow)

    @Test
    fun `switch on uses the Nightscout settings`() {
        followNightscout(true)
        val sut = TidepoolConnectivitySettings(preferences)

        assertThat(sut.useCellular).isTrue()
        assertThat(sut.useRoaming).isTrue()
        assertThat(sut.wifiSsids).isEmpty()
        assertThat(sut.useOnBattery).isTrue()
    }

    @Test
    fun `switch off uses the Tidepool settings`() {
        followNightscout(false)
        val sut = TidepoolConnectivitySettings(preferences)

        assertThat(sut.useCellular).isFalse()
        assertThat(sut.useRoaming).isFalse()
        assertThat(sut.useWifi).isTrue()
        assertThat(sut.wifiSsids).isEqualTo("home")
        assertThat(sut.useOnBattery).isFalse()
        assertThat(sut.useOnCharging).isTrue()
    }

    @Test
    fun `Tidepool waits for WiFi and a charger while Nightscout may use cellular`() {
        followNightscout(false)
        val receiverStatusStore: ReceiverStatusStore = mock()
        whenever(receiverStatusStore.networkStatusFlow).thenReturn(MutableStateFlow(null))
        whenever(receiverStatusStore.chargingStatusFlow).thenReturn(MutableStateFlow(null))
        val logger: AAPSLogger = mock()
        val rh: TextResolver = mock()
        val tidepool = TidepoolReceiverDelegate(logger, rh, preferences, receiverStatusStore)

        val cellular = ReceiverStatusStore.NetworkStatus(mobileConnected = true)
        assertThat(tidepool.calculateStatus(cellular)).isFalse()
        assertThat(tidepool.calculateStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssid = "home"))).isTrue()
        assertThat(tidepool.calculateStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = true, ssid = "cafe"))).isFalse()
        assertThat(tidepool.calculateStatus(ReceiverStatusStore.ChargingStatus(isCharging = false, batteryLevel = 50))).isFalse()
        assertThat(tidepool.calculateStatus(ReceiverStatusStore.ChargingStatus(isCharging = true, batteryLevel = 50))).isTrue()

        // The same phone state is fine for Nightscout
        followNightscout(true)
        assertThat(tidepool.calculateStatus(cellular)).isTrue()
    }

    @Test
    fun `a change of a Tidepool setting is reported`() = runTest(UnconfinedTestDispatcher()) {
        followNightscout(false)
        val sut = TidepoolConnectivitySettings(preferences)
        val change = async { sut.networkSettingChanges.first() }

        tidepoolCellular.value = false

        change.await() // only returns when the change was reported
        assertThat(change.isCompleted).isTrue()
    }
}
