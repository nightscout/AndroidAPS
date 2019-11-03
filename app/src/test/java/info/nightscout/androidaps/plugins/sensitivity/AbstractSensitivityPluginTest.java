package info.nightscout.androidaps.plugins.sensitivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class})
public class AbstractSensitivityPluginTest {

    private class SensitivityTestClass extends AbstractSensitivityPlugin {

        public SensitivityTestClass() {
            super(null);
        }

        public SensitivityTestClass(PluginDescription pluginDescription) {
            super(pluginDescription);
        }

        @Override
        public AutosensResult detectSensitivity(IobCobCalculatorPlugin plugin, long fromTime, long toTime) {
            return null;
        }
    }

    @Test
    public void fillResultTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();

        SensitivityTestClass sut = new SensitivityTestClass();

        AutosensResult ar = sut.fillResult(1d, 1d, "1",
                "1.2", "1", 12, 0.7d, 1.2d);
        assertEquals(1, ar.ratio, 0.01);

        ar = sut.fillResult(1.2d, 1d, "1",
                "1.2", "1", 40, 0.7d, 1.2d);
        assertEquals(1.16, ar.ratio, 0.01);

        ar = sut.fillResult(1.2d, 1d, "1",
                "1.2", "1", 50, 0.7d, 1.2d);
        assertEquals(1.2, ar.ratio, 0.01);

        ar = sut.fillResult(1.2d, 1d, "1",
                "1.2", "1", 50, 0.7d, 1.1d);
        assertEquals(1.1, ar.ratio, 0.01);
    }


}
