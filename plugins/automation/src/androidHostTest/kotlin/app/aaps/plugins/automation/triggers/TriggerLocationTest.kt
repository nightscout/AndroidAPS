package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.asJsonObject
import app.aaps.plugins.automation.elements.InputLocationMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerLocationTest : TriggerTestBase() {

    // The distance itself is measured by the platform now (`LastLocationDataContainer` still calls
    // `Location.distanceTo`), so these tests state the distance directly. That also fixes the
    // GOING_OUT case, which used to be skipped because a `Location` could not be mocked here.
    @BeforeEach fun mock() {
        whenever(lastKnownLocation.distanceTo(any(), any())).thenReturn(1.0)
    }

    @Test fun copyConstructorTest() = runTest {
        val t = TriggerLocation(triggerDeps)
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

    @Test fun shouldRunTest() = runTest {
        var t = TriggerLocation(triggerDeps)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        // No position reported yet, so there is nothing to compare against.
        whenever(lastKnownLocation.distanceTo(any(), any())).thenReturn(null)
        assertThat(t.shouldRun()).isFalse()
        // 1 m away, inside the 2 m radius.
        whenever(lastKnownLocation.distanceTo(any(), any())).thenReturn(1.0)
        assertThat(t.shouldRun()).isTrue()
        t = TriggerLocation(triggerDeps)
        t.distance.setValue(-500.0)
        assertThat(t.shouldRun()).isFalse()

        //Test of GOING_IN - last mode should be OUTSIDE, and current mode should be INSIDE
        t = TriggerLocation(triggerDeps)
        t.distance.setValue(50.0)
        t.lastMode = t.currentMode(55.0)
        whenever(lastKnownLocation.distanceTo(any(), any())).thenReturn(5.0)
        t.modeSelected.value = InputLocationMode.Mode.GOING_IN
        assertThat(InputLocationMode.Mode.OUTSIDE).isEqualTo(t.lastMode)
        assertThat(InputLocationMode.Mode.INSIDE).isEqualTo(t.currentMode(5.0))
        assertThat(t.shouldRun()).isTrue()

        //Test of GOING_OUT - last mode should be INSIDE, and current mode should be OUTSIDE
        t = TriggerLocation(triggerDeps)
        t.distance.setValue(50.0)
        t.lastMode = t.currentMode(5.0)
        whenever(lastKnownLocation.distanceTo(any(), any())).thenReturn(55.0)
        t.modeSelected.value = InputLocationMode.Mode.GOING_OUT
        assertThat(InputLocationMode.Mode.INSIDE).isEqualTo(t.lastMode)
        assertThat(InputLocationMode.Mode.OUTSIDE).isEqualTo(t.currentMode(55.0))
        assertThat(t.shouldRun()).isTrue()
    }

    private var locationJson = "{\"data\":{\"mode\":\"OUTSIDE\",\"distance\":2,\"latitude\":213,\"name\":\"\",\"longitude\":212},\"type\":\"TriggerLocation\"}"
    @Test fun toJSONTest() = runTest {
        val t = TriggerLocation(triggerDeps)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.OUTSIDE
//        t.modeSelected = t.modeSelected.value
        // Compared as JSON, not as text. kotlinx writes the keys in insertion order where org.json
        // wrote them in hash order, and writes a whole double as 213.0 where org.json wrote 213.
        // Both are the same document, and it is the document that is the stored contract.
        JSONAssert.assertEquals(locationJson, t.toJSON(), true)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t = TriggerLocation(triggerDeps)
        t.latitude.setValue(213.0)
        t.longitude.setValue(212.0)
        t.distance.setValue(2.0)
        t.modeSelected.value = InputLocationMode.Mode.INSIDE
        val t2 = triggerFactory.instantiate(t.toJSON().asJsonObject()) as TriggerLocation
        assertThat(t2.latitude.value).isWithin(0.01).of(t.latitude.value)
        assertThat(t2.longitude.value).isWithin(0.01).of(t.longitude.value)
        assertThat(t2.distance.value).isWithin(0.01).of(t.distance.value)
        assertThat(t2.modeSelected.value).isEqualTo(t.modeSelected.value)
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(TriggerLocation(triggerDeps).friendlyName()).isEqualTo(AutomationStrings.location)
    }

    @Test fun friendlyDescriptionTest() = runTest {
        assertThat(TriggerLocation(triggerDeps).friendlyDescription()).isEqualTo("locationis") // nothing stubbed, so the name is the fallback
    }
}
