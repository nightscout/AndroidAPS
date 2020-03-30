package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, IobCobCalculatorPlugin.class, SP.class, L.class, BluetoothAdapter.class, BluetoothProfile.class})
public class TriggerBTDeviceTest {

    long now = 1514766900000L;
    String someName = "Headset";
    String btJson = "{\"data\":{\"comparator\":\"EXISTS\",\"lastRun\":0,\"name\":\"Headset\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBTDevice\"}";

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);
        when(SP.getInt(any(), any())).thenReturn(48);


    }

    @Test
    public void comparator() {
        TriggerBTDevice t = new TriggerBTDevice().comparator(ComparatorExists.Compare.EXISTS);
        Assert.assertEquals(t.comparator.getValue(), ComparatorExists.Compare.EXISTS);
    }

    @Test
    public void shouldRun() {
        BluetoothAdapter btAdapter = PowerMockito.mock(BluetoothAdapter.class);
        PowerMockito.mockStatic(BluetoothAdapter.class);
        PowerMockito.mockStatic(BluetoothProfile.class);
        TriggerBTDevice t = new TriggerBTDevice().comparator(ComparatorExists.Compare.EXISTS);
        Assert.assertFalse(t.shouldRun()); // no bluetooth adapter
        when(BluetoothAdapter.getDefaultAdapter()).thenReturn(btAdapter);
        when(btAdapter.isEnabled()).thenReturn(true);
        Assert.assertFalse(t.shouldRun()); // no device connected
        when(btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)).thenReturn(BluetoothProfile.STATE_CONNECTED);
        t.setDeviceName(someName);
        Assert.assertFalse(t.shouldRun()); // no mocked connection
        t.setConnectedState(true);
        Assert.assertTrue(t.shouldRun());

    }

    @Test
    public void toJSON() {
        TriggerBTDevice t = new TriggerBTDevice().comparator(ComparatorExists.Compare.EXISTS);
        t.setDeviceName(someName);
        Assert.assertEquals(btJson, t.toJSON());
    }

    @Test
    public void fromJSON() throws JSONException {
        TriggerBTDevice t = new TriggerBTDevice().comparator(ComparatorExists.Compare.EXISTS);
        t.setDeviceName(someName);
        TriggerBTDevice t2 = (TriggerBTDevice) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(ComparatorExists.Compare.EXISTS, t2.getComparator().getValue());
        Assert.assertEquals("Headset", t2.getDeviceName());
    }

    @Test
    public void friendlyName() {
    }

    @Test
    public void friendlyDescription() {
    }

    @Test
    public void icon() {
        Assert.assertEquals(Optional.of(R.drawable.ic_bluetooth_white_48dp), new TriggerBTDevice().icon());
    }

    @Test
    public void duplicate() {
        TriggerBTDevice t = new TriggerBTDevice().comparator(ComparatorExists.Compare.EXISTS);
        t.setDeviceName(someName);
        TriggerBTDevice t1 = (TriggerBTDevice) t.duplicate();
        Assert.assertEquals("Headset", t1.getDeviceName());
        Assert.assertEquals(ComparatorExists.Compare.EXISTS, t.getComparator().getValue());
    }

    @Test
    public void lastRun() {
        TriggerBTDevice t = new TriggerBTDevice().lastRun(now);
        Assert.assertEquals(now, t.lastRun);
    }

    @Test
    public void generateDialog() {
    }
}