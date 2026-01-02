package app.aaps.plugins.automation.triggers

import android.location.Location
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputLocationMode
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class TriggerLocationTest : TriggerTestBase() {

    @BeforeEach fun mock() {
        whenever(locationDataContainer.lastLocation).thenReturn(mockedLocation())
    }

    @Test fun copyConstructorTest() {
        val t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.INSIDE
        val t1 = t.duplicate() as TriggerLocation
        assertThat(t1.latitude.value).isWithin(0.01).of(213.0)
        assertThat(t1.longitude.value).isWithin(0.01).of(212.0)
        assertThat(t1.distance.value).isWithin(0.01).of(2.0)
        assertThat(t1.modeSelected.value).isEqualTo(InputLocationMode.Mode.INSIDE)
    }

    @Test fun shouldRunTest() {
        var t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        //        t.modeSelected.setValue(InputLocationMode.Mode.OUTSIDE);
        whenever(locationDataContainer.lastLocation).thenReturn(null)
        assertThat(t.shouldRun()).isFalse()
        whenever(locationDataContainer.lastLocation).thenReturn(mockedLocation())
        assertThat(t.shouldRun()).isTrue()
        t = TriggerLocation(injector)
        t.distance.setValue(-500.0)
        assertThat(t.shouldRun()).isFalse()

        //Test of GOING_IN - last mode should be OUTSIDE, and current mode should be INSIDE
        t = TriggerLocation(injector)
        t.distance.setValue(50.0)
        t.lastMode = t.currentMode(55.0)
        whenever(locationDataContainer.lastLocation).thenReturn(null)
        whenever(locationDataContainer.lastLocation).thenReturn(mockedLocationOut())
        t.modeSelected.value = InputLocationMode.Mode.GOING_IN
        assertThat(InputLocationMode.Mode.OUTSIDE).isEqualTo(t.lastMode)
        assertThat(InputLocationMode.Mode.INSIDE).isEqualTo(t.currentMode(5.0))
        assertThat(t.shouldRun()).isTrue()

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
        assertThat(t.toJSON()).isEqualTo(locationJson)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t = TriggerLocation(injector)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.INSIDE
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerLocation
        assertThat(t2.latitude.value).isWithin(0.01).of(t.latitude.value)
        assertThat(t2.longitude.value).isWithin(0.01).of(t.longitude.value)
        assertThat(t2.distance.value).isWithin(0.01).of(t.distance.value)
        assertThat(t2.modeSelected.value).isEqualTo(t.modeSelected.value)
    }

    @Test fun friendlyNameTest() {
        assertThat(TriggerLocation(injector).friendlyName()).isEqualTo(R.string.location)
    }

    @Test fun friendlyDescriptionTest() {
        assertThat(TriggerLocation(injector).friendlyDescription()).isNull() //not mocked    }
    }

    @Test fun iconTest() {
        assertThat(TriggerLocation(injector).icon().get()).isEqualTo(R.drawable.ic_location_on)
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
