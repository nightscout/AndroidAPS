package info.nightscout.androidaps.plugins.NSClientInternal;

import android.content.Context;
import com.squareup.otto.Bus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.utils.SP;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, Context.class})
public class NsClientReceiverDelegateTest {

    private NsClientReceiverDelegate sut;

    @Before
    public void prepare() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();

        Bus bus = MainApp.bus();
        Context context = MainApp.instance().getApplicationContext();

        sut = new NsClientReceiverDelegate(context, bus);
    }

    @Test
    public void testCalculateStatusChargingState() {
        PowerMockito.mockStatic(SP.class);
        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(false);
        EventChargingState ev = new EventChargingState(true);
        assertTrue(sut.calculateStatus(ev));
        ev = new EventChargingState(false);
        assertTrue(sut.calculateStatus(ev));

        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(true);
        ev = new EventChargingState(true);
        assertTrue(sut.calculateStatus(ev));
        ev = new EventChargingState(false);
        assertTrue(!sut.calculateStatus(ev));
    }

    @Test
    public void testCalculateStatusNetworkState() {
        PowerMockito.mockStatic(SP.class);
        // wifiOnly = false
        // allowRoaming = false as well
        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(false);
        when(SP.getString(anyInt(), anyString())).thenReturn("");
        EventNetworkChange ev = new EventNetworkChange();
        ev.ssid = "<unknown ssid>";

        ev.mobileConnected = true;
        ev.wifiConnected = true;
        assertTrue(sut.calculateStatus(ev));
        ev.wifiConnected = false;
        assertTrue(sut.calculateStatus(ev));

        // wifiOnly = true
        // allowRoaming = true as well
        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(true);
        ev.wifiConnected = true;
        assertTrue(sut.calculateStatus(ev));
        ev.wifiConnected = false;
        assertTrue(!sut.calculateStatus(ev));

        // wifiOnly = false
        // allowRoaming = false as well
        when(SP.getBoolean(anyInt(), anyBoolean())).thenReturn(false);
        ev.wifiConnected = false;
        ev.roaming = true;
        assertTrue(!sut.calculateStatus(ev));

        // wifiOnly = false
        // allowRoaming = true
        when(SP.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(false);
        when(SP.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(true);
        ev.wifiConnected = false;
        ev.roaming = true;
        assertTrue(sut.calculateStatus(ev));

        // wifiOnly = true
        // allowRoaming = true
        when(SP.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(true);
        when(SP.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(true);
        ev.wifiConnected = false;
        ev.roaming = true;
        assertTrue(!sut.calculateStatus(ev));

        // wifiOnly = true
        // allowRoaming = true
        when(SP.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(true);
        when(SP.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(true);
        ev.wifiConnected = true;
        ev.roaming = true;
        assertTrue(sut.calculateStatus(ev));

        // wifiOnly = false
        // allowRoaming = false
        when(SP.getBoolean(R.string.key_ns_wifionly, false)).thenReturn(false);
        when(SP.getBoolean(R.string.key_ns_allowroaming, true)).thenReturn(false);
        ev.wifiConnected = true;
        ev.roaming = true;
        assertTrue(sut.calculateStatus(ev));
    }
}
