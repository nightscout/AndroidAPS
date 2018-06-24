package info.nightscout.androidaps.plugins.Sensitivity;

import org.junit.Test;

import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;

import static org.junit.Assert.assertEquals;

public class AbstractSensitivityPluginTest {

    private class SensitivityTestClass extends AbstractSensitivityPlugin {

        public SensitivityTestClass() {
            super(null);
        }

        public SensitivityTestClass(PluginDescription pluginDescription) {
            super(pluginDescription);
        }

        @Override
        public AutosensResult detectSensitivity(long fromTime, long toTime) {
            return null;
        }
    }

    @Test
    public void fillResultTest() {
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
