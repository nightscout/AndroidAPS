package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.location.Location;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.services.LocationService;
import info.nightscout.androidaps.utils.DateUtil;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, DateUtil.class, LocationService.class})

public class TriggerLocationTest {

    long now = 1514766900000L;

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();

        PowerMockito.mockStatic(DateUtil.class);
        PowerMockito.mockStatic(LocationService.class);
        when(DateUtil.now()).thenReturn(now);
        PowerMockito.when(LocationService.getLastLocation()).thenReturn(mockedLocation());


        MockitoAnnotations.initMocks(this);

    }


    @Test
    public void copyConstructorTest() {
        TriggerLocation t = new TriggerLocation();
        t.latitude.setValue(213);
        t.longitude.setValue(212);
        t.distance.setValue(2);

        TriggerLocation t1 = (TriggerLocation) t.duplicate();
        Assert.assertEquals(213d, t.latitude.getValue(), 0.01d);
        Assert.assertEquals(212d, t.longitude.getValue(), 0.01d);
        Assert.assertEquals(2d, t.distance.getValue(), 0.01d);
    }

    @Test
    public void shouldRunTest() {
        TriggerLocation t = new TriggerLocation();
        t.latitude.setValue(213);
        t.longitude.setValue(212);
        t.distance.setValue(2);
        PowerMockito.when(LocationService.getLastLocation()).thenReturn(null);
        Assert.assertFalse(t.shouldRun());
        PowerMockito.when(LocationService.getLastLocation()).thenReturn(mockedLocation());
        Assert.assertTrue(t.shouldRun());
        t.lastRun(now - 1);
        Assert.assertFalse(t.shouldRun());

        t = new TriggerLocation();
        t.distance.setValue(-500);
        Assert.assertFalse(t.shouldRun());
    }

    String locationJson = "{\"data\":{\"distance\":2,\"lastRun\":0,\"latitude\":213,\"name\":\"\",\"longitude\":212},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerLocation\"}";

    @Test
    public void toJSONTest() {
        TriggerLocation t = new TriggerLocation();
        t.latitude.setValue(213);
        t.longitude.setValue(212);
        t.distance.setValue(2);
        Assert.assertEquals(locationJson, t.toJSON());
    }

    @Test
    public void fromJSONTest() throws JSONException {
        TriggerLocation t = new TriggerLocation();
        t.latitude.setValue(213);
        t.longitude.setValue(212);
        t.distance.setValue(2);

        TriggerLocation t2 = (TriggerLocation) Trigger.instantiate(new JSONObject(t.toJSON()));
        Assert.assertEquals(t.latitude.getValue(), t2.latitude.getValue(), 0.01d);
        Assert.assertEquals(t.longitude.getValue(), t2.longitude.getValue(), 0.01d);
        Assert.assertEquals(t.distance.getValue(), t2.distance.getValue(), 0.01d);
    }

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.location, new TriggerLocation().friendlyName());
    }

    @Test
    public void friendlyDescriptionTest() {
        Assert.assertEquals(null, new TriggerLocation().friendlyDescription()); //not mocked    }
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_location_on), new TriggerLocation().icon());

    }

    @Test
    public void setLatitudeTest() {
        TriggerLocation t = new TriggerLocation();
        t.setLatitude(212);
        Assert.assertEquals(t.latitude.getValue(), 212, 0d);
    }

    @Test
    public void setLongitudeTest() {
        TriggerLocation t = new TriggerLocation();
        t.setLongitude(213);
        Assert.assertEquals(t.longitude.getValue(), 213, 0d);
    }

    @Test
    public void setdistanceTest() {
        TriggerLocation t = new TriggerLocation();
        t.setdistance(2);
        Assert.assertEquals(t.distance.getValue(), 2, 0d);
    }

    @Test
    public void lastRunTest() {
        TriggerLocation t = new TriggerLocation();
        t.lastRun(now);
        Assert.assertEquals(t.lastRun, 1514766900000L, 0d);
    }

    public Location mockedLocation() {
        Location newLocation = new Location("test");
        newLocation.setLatitude(10);
        newLocation.setLongitude(11);
        newLocation.setAccuracy(1f);
        return newLocation;
    }
}