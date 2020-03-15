package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.SP
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class InputProfileNameTest : TriggerTestBase() {

    @Test fun setValue() {
        val inputProfileName = InputProfileName(injector, "Test")
        Assert.assertEquals("Test", inputProfileName.value)
        inputProfileName.value = "Test2"
        Assert.assertEquals("Test2", inputProfileName.value)
    }
}