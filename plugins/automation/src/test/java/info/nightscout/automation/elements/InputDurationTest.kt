package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputDurationTest : TriggerTestBase() {

    @Test fun setValueTest() {
        var i = InputDuration(5, InputDuration.TimeUnit.MINUTES)
        Assertions.assertEquals(5, i.value)
        Assertions.assertEquals(InputDuration.TimeUnit.MINUTES, i.unit)
        i = InputDuration(5, InputDuration.TimeUnit.HOURS)
        Assertions.assertEquals(5, i.value)
        Assertions.assertEquals(InputDuration.TimeUnit.HOURS, i.unit)
        Assertions.assertEquals(5 * 60, i.getMinutes())
        i.setMinutes(60)
        Assertions.assertEquals(1, i.value)
    }
}