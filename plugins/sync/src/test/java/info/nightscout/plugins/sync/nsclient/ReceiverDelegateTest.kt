package info.nightscout.plugins.sync.nsclient

import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.plugins.sync.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ReceiverDelegateTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    private val rxBus = RxBus(aapsSchedulers, aapsLogger)

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
        Assertions.assertTrue(sut.calculateStatus(EventChargingState(false, 0)))
        Assertions.assertFalse(sut.calculateStatus(EventChargingState(true, 0)))
        `when`(sp.getBoolean(R.string.key_ns_battery, true)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_charging, true)).thenReturn(true)
        Assertions.assertTrue(sut.calculateStatus(EventChargingState(true, 0)))
        Assertions.assertFalse(sut.calculateStatus(EventChargingState(false, 0)))
    }

    @Test
    fun testCalculateStatusNetworkState() {
        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = true)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = false)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true)))
        Assertions.assertFalse(sut.calculateStatus(EventNetworkChange()))

        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("test 1")
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = true)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, wifiConnected = false, roaming = false)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true)))
        Assertions.assertFalse(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = false, wifiConnected = true)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "test 1", mobileConnected = true, wifiConnected = true)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "test 1", mobileConnected = false, wifiConnected = true)))
        Assertions.assertFalse(sut.calculateStatus(EventNetworkChange()))

        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(wifiConnected = true)))
        Assertions.assertFalse(sut.calculateStatus(EventNetworkChange()))
        Assertions.assertFalse(sut.calculateStatus(EventNetworkChange(mobileConnected = true)))

        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(false)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false)))
        Assertions.assertFalse(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true)))

        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false)))
        Assertions.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true)))
    }
}