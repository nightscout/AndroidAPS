package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, TreatmentsPlugin.class, ProfileFunctions.class})
public class InputProfileNameTest {
    String profileName = "Test";
    InputProfileName inputProfileName = new InputProfileName(profileName);

    @Test
    public void getSetValue() {
        inputProfileName = new InputProfileName("Test");
        Assert.assertEquals("Test", inputProfileName.getValue());
        inputProfileName.setValue("Test2");
        Assert.assertEquals("Test2", inputProfileName.getValue());
    }
}