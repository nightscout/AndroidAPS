package info.nightscout.androidaps.db;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.logging.Logger;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, Logger.class, L.class, SP.class})
public class BgReadingTest {
    private BgReading bgReading = new BgReading();
    @Mock
    GlucoseStatus glucoseStatus;

    @Test
    public void valueToUnits() {
        bgReading.value = 18;
        assertEquals(18, bgReading.valueToUnits(Constants.MGDL)*1, 0.01d);
        assertEquals(1, bgReading.valueToUnits(Constants.MMOL)*1, 0.01d);
    }

    @Test
    public void directionToSymbol() {
        bgReading = new BgReading();
        bgReading.direction = "DoubleDown";
        assertEquals("\u21ca", bgReading.directionToSymbol());
        bgReading.direction = "SingleDown";
        assertEquals("\u2193", bgReading.directionToSymbol());
        bgReading.direction = "FortyFiveDown";
        assertEquals("\u2198", bgReading.directionToSymbol());
        bgReading.direction = "Flat";
        assertEquals("\u2192", bgReading.directionToSymbol());
        bgReading.direction = "FortyFiveUp";
        assertEquals("\u2197", bgReading.directionToSymbol());
        bgReading.direction = "SingleUp";
        assertEquals("\u2191", bgReading.directionToSymbol());
        bgReading.direction = "DoubleUp";
        assertEquals("\u21c8", bgReading.directionToSymbol());
        bgReading.direction = "OUT OF RANGE";
        assertEquals("??", bgReading.directionToSymbol());

    }

    @Test
    public void calculateDirection() throws Exception {
        assertEquals("??", bgReading.calculateDirection());


    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
    }
}