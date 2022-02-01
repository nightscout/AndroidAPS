package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`

class NsClientReceiverDelegateTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper

    lateinit var receiverStatusStore: ReceiverStatusStore
    val rxBus = RxBus(aapsSchedulers, aapsLogger)

    private var sut: NsClientReceiverDelegate? = null

    @Before fun prepare() {
        receiverStatusStore = ReceiverStatusStore(context, rxBus)
        `when`(sp.getLong(anyInt(), anyLong())).thenReturn(0L)
        `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(false)
        `when`(sp.getInt(anyInt(), anyInt())).thenReturn(0)
        `when`(sp.getString(anyInt(), anyString())).thenReturn("")

        sut = NsClientReceiverDelegate(rxBus, rh, sp, receiverStatusStore)
    }

    @Test fun testCalculateStatusChargingState() {
        `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(false)
        var ev = EventChargingState(true, 0)
        Assert.assertTrue(sut!!.calculateStatus(ev))
        ev = EventChargingState(false, 0)
        Assert.assertTrue(sut!!.calculateStatus(ev))
        `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(true)
        ev = EventChargingState(true, 0)
        Assert.assertTrue(sut!!.calculateStatus(ev))
        ev = EventChargingState(false, 0)
        Assert.assertTrue(!sut!!.calculateStatus(ev))
    }

    @Test fun testCalculateStatusNetworkState() {
        // wifiOnly = false
        // allowRoaming = false as well
        `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(false)
        `when`(sp.getString(anyInt(), anyString())).thenReturn("")
        val ev = EventNetworkChange()
        ev.ssid = "<unknown ssid>"
        ev.mobileConnected = true
        ev.wifiConnected = true
        Assert.assertTrue(sut!!.calculateStatus(ev))
        ev.ssid = "test"
        `when`(sp.getString(anyInt(), anyString())).thenReturn("test")
        Assert.assertTrue(sut!!.calculateStatus(ev))
        ev.ssid = "test"
        Assert.assertTrue(sut!!.calculateStatus(ev))
        ev.wifiConnected = false
        Assert.assertTrue(sut!!.calculateStatus(ev))

        // wifiOnly = true
        // allowRoaming = true as well
        `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(true)
        ev.wifiConnected = true
        Assert.assertTrue(sut!!.calculateStatus(ev))
        ev.wifiConnected = false
        Assert.assertTrue(!sut!!.calculateStatus(ev))

        // wifiOnly = false
        // allowRoaming = false as well
        `when`(sp.getBoolean(anyInt(), anyBoolean())).thenReturn(false)
        ev.wifiConnected = false
        ev.roaming = true
        Assert.assertTrue(!sut!!.calculateStatus(ev))

        // wifiOnly = false
        // allowRoaming = true
        `when`(sp.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(true)
        ev.wifiConnected = false
        ev.roaming = true
        Assert.assertTrue(sut!!.calculateStatus(ev))

        // wifiOnly = true
        // allowRoaming = true
        `when`(sp.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(true)
        ev.wifiConnected = false
        ev.roaming = true
        Assert.assertTrue(!sut!!.calculateStatus(ev))

        // wifiOnly = true
        // allowRoaming = true
        `when`(sp.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(true)
        `when`(sp.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(true)
        ev.wifiConnected = true
        ev.roaming = true
        Assert.assertTrue(sut!!.calculateStatus(ev))

        // wifiOnly = false
        // allowRoaming = false
        `when`(sp.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(false)
        `when`(sp.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(false)
        ev.wifiConnected = true
        ev.roaming = true
        Assert.assertTrue(sut!!.calculateStatus(ev))
    }
}