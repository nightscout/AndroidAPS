package info.nightscout.androidaps.plugins.general.automation.triggers;

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
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.receivers.NetworkChangeReceiver;
import info.nightscout.androidaps.utils.DateUtil;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, NetworkChangeReceiver.class, DateUtil.class})
public class TriggerWifiSsidTest {

    long now = 1514766900000L;

    @Test
    public void shouldRunTest() {
        EventNetworkChange e = new EventNetworkChange();
        when(NetworkChangeReceiver.getLastEvent()).thenReturn(e);

        TriggerWifiSsid t = new TriggerWifiSsid().setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL);

        e.setWifiConnected(false);
        Assert.assertFalse(t.shouldRun());

        e.setWifiConnected(true);
        e.setSsid("otherSSID");
        Assert.assertFalse(t.shouldRun());

        e.setWifiConnected(true);
        e.setSsid("aSSID");
        Assert.assertTrue(t.shouldRun());

        t.lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

        t = new TriggerWifiSsid().setValue("aSSID").comparator(Comparator.Compare.IS_NOT_AVAILABLE);
        e.setWifiConnected(false);
        Assert.assertTrue(t.shouldRun());

        // no network data
        when(NetworkChangeReceiver.getLastEvent()).thenReturn(null);
        Assert.assertFalse(t.shouldRun());
    }

    @Test
    public void copyConstructorTest() {
        TriggerWifiSsid t = new TriggerWifiSsid().setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL_OR_LESSER);
        TriggerWifiSsid t1 = (TriggerWifiSsid) t.duplicate();
        Assert.assertEquals("aSSID", t1.getValue());
        Assert.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.getComparator().getValue());
    }

    String json = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"lastRun\":0,\"ssid\":\"aSSID\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerWifiSsid\"}";

    @Test
    public void toJSONTest() {
        TriggerWifiSsid t = new TriggerWifiSsid().setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL);
        Assert.assertEquals(json, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerWifiSsid t = new TriggerWifiSsid().setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL);

        TriggerWifiSsid t2 = (TriggerWifiSsid) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(Comparator.Compare.IS_EQUAL, t2.getComparator().getValue());
        Assert.assertEquals("aSSID", t2.getValue());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_network_wifi), new TriggerWifiSsid().icon());
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.ns_wifi_ssids, new TriggerWifiSsid().friendlyName());
    }

    @Test
    public void friendlyDescriptionTest() {
        Assert.assertEquals(null, new TriggerWifiSsid().friendlyDescription()); //not mocked
    }

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();

        PowerMockito.mockStatic(NetworkChangeReceiver.class);

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(now);
    }

}
