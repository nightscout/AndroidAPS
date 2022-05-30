package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class NsClientReceiverDelegateTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper

    lateinit var receiverStatusStore: ReceiverStatusStore
    val rxBus = RxBus(aapsSchedulers, aapsLogger)

    private lateinit var sut: NsClientReceiverDelegate

    @Before
    fun prepare() {
        receiverStatusStore = ReceiverStatusStore(context, rxBus)
        // `when`(sp.getLong(anyInt(), anyLong())).thenReturn(0L)
        // `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(false)
        // `when`(sp.getInt(anyInt(), anyInt())).thenReturn(0)
        // `when`(sp.getString(anyInt(), anyString())).thenReturn("")

        sut = NsClientReceiverDelegate(rxBus, rh, sp, receiverStatusStore)
    }

    @Test
    fun testCalculateStatusChargingState() {
        `when`(sp.getBoolean(R.string.key_ns_battery, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_charging, true)).thenReturn(false)
        Assert.assertTrue(sut.calculateStatus(EventChargingState(false, 0)))
        Assert.assertFalse(sut.calculateStatus(EventChargingState(true, 0)))
        `when`(sp.getBoolean(R.string.key_ns_battery, true)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_charging, true)).thenReturn(true)
        Assert.assertTrue(sut.calculateStatus(EventChargingState(true, 0)))
        Assert.assertFalse(sut.calculateStatus(EventChargingState(false, 0)))
    }

    @Test
    fun testCalculateStatusNetworkState() {
        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("")
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "<unknown ssid>", mobileConnected = true, wifiConnected = true)))

        `when`(sp.getString(R.string.key_ns_wifi_ssids, "")).thenReturn("test")
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(ssid = "test", mobileConnected = true, wifiConnected = true)))
        Assert.assertFalse(sut.calculateStatus(EventNetworkChange(ssid = "unknown", mobileConnected = true, wifiConnected = true)))
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true)))

        // wifiOnly = true
        // allowRoaming = true as well
        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(wifiConnected = true)))
        Assert.assertFalse(sut.calculateStatus(EventNetworkChange()))
        Assert.assertFalse(sut.calculateStatus(EventNetworkChange(mobileConnected = true)))

        // wifiOnly = false
        // allowRoaming = false as well
        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(false)
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false)))
        Assert.assertFalse(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true)))

        // wifiOnly = false
        // allowRoaming = true
        `when`(sp.getBoolean(R.string.key_ns_cellular, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_wifi, true)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allow_roaming, true)).thenReturn(true)
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = false)))
        Assert.assertTrue(sut.calculateStatus(EventNetworkChange(mobileConnected = true, roaming = true)))
    }
}