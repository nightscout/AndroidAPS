package info.nightscout.automation.triggers

import android.location.Location
import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputLocationMode
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerLocationTest : TriggerTestBase() {

    var now = 1514766900000L

    @BeforeEach fun mock() {
        `when`(dateUtil.now()).thenReturn(now)
        `when`(locationDataContainer.lastLocation).thenReturn(mockedLocation())
    }

    @Test fun copyConstructorTest() {
        val t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.INSIDE
        val t1 = t.duplicate() as TriggerLocation
        Assert.assertEquals(213.0, t1.latitude.value, 0.01)
        Assert.assertEquals(212.0, t1.longitude.value, 0.01)
        Assert.assertEquals(2.0, t1.distance.value, 0.01)
        Assert.assertEquals(InputLocationMode.Mode.INSIDE, t1.modeSelected.value)
    }

    @Test fun shouldRunTest() {
        var t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        //        t.modeSelected.setValue(InputLocationMode.Mode.OUTSIDE);
        `when`(locationDataContainer.lastLocation).thenReturn(null)
        Assert.assertFalse(t.shouldRun())
        `when`(locationDataContainer.lastLocation).thenReturn(mockedLocation())
        Assert.assertTrue(t.shouldRun())
        t = TriggerLocation(injector)
        t.distance.setValue(-500.0)
        Assert.assertFalse(t.shouldRun())

        //Test of GOING_IN - last mode should be OUTSIDE, and current mode should be INSIDE
        t = TriggerLocation(injector)
        t.distance.setValue(50.0)
        t.lastMode = t.currentMode(55.0)
        `when`(locationDataContainer.lastLocation).thenReturn(null)
        `when`(locationDataContainer.lastLocation).thenReturn(mockedLocationOut())
        t.modeSelected.value = InputLocationMode.Mode.GOING_IN
        Assert.assertEquals(t.lastMode, InputLocationMode.Mode.OUTSIDE)
        Assert.assertEquals(t.currentMode(5.0), InputLocationMode.Mode.INSIDE)
        Assert.assertTrue(t.shouldRun())

        //Test of GOING_OUT - last mode should be INSIDE, and current mode should be OUTSIDE
        // Currently unavailable due to problems with Location mocking
    }

    private var locationJson = "{\"data\":{\"mode\":\"OUTSIDE\",\"distance\":2,\"latitude\":213,\"name\":\"\",\"longitude\":212},\"type\":\"TriggerLocation\"}"
    @Test fun toJSONTest() {
        val t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.OUTSIDE
//        t.modeSelected = t.modeSelected.value
        Assert.assertEquals(locationJson, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.INSIDE
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerLocation
        Assert.assertEquals(t.latitude.value, t2.latitude.value, 0.01)
        Assert.assertEquals(t.longitude.value, t2.longitude.value, 0.01)
        Assert.assertEquals(t.distance.value, t2.distance.value, 0.01)
        Assert.assertEquals(t.modeSelected.value, t2.modeSelected.value)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.location, TriggerLocation(injector).friendlyName())
    }

    @Test fun friendlyDescriptionTest() {
        Assert.assertEquals(null, TriggerLocation(injector).friendlyDescription()) //not mocked    }
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_location_on), TriggerLocation(injector).icon())
    }

    private fun mockedLocation(): Location {
        val newLocation = Location("test")
        newLocation.latitude = 10.0
        newLocation.longitude = 11.0
        newLocation.accuracy = 1f
        return newLocation
    }

    private fun mockedLocationOut(): Location {
        val newLocation = Location("test")
        newLocation.latitude = 12.0
        newLocation.longitude = 13.0
        newLocation.accuracy = 1f
        return newLocation
    }
}