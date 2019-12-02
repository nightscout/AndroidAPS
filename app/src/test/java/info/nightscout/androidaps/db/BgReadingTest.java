package info.nightscout.androidaps.db;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, Logger.class, L.class, SP.class, GlucoseStatus.class})
public class BgReadingTest {
    private BgReading bgReading = new BgReading();

    @Test
    public void valueToUnits() {
        bgReading.value = 18;
        assertEquals(18, bgReading.valueToUnits(Constants.MGDL) * 1, 0.01d);
        assertEquals(1, bgReading.valueToUnits(Constants.MMOL) * 1, 0.01d);
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
    public void dateTest() {
        bgReading = new BgReading();
        long now = System.currentTimeMillis();
        bgReading.date = now;
        Date nowDate = new Date(now);
        assertEquals(now, bgReading.date(now).date);
        assertEquals(now, bgReading.date(nowDate).date);
    }

    @Test
    public void valueTest() {
        bgReading = new BgReading();
        double valueToSet = 81; // 4.5 mmol
        assertEquals(81d, bgReading.value(valueToSet).value, 0.01d);
    }

    @Test
    public void copyFromTest() {
        bgReading = new BgReading();
        BgReading copy = new BgReading();
        bgReading.value = 81;
        long now = System.currentTimeMillis();
        bgReading.date = now;
        copy.date = now;

        copy.copyFrom(bgReading);

        assertEquals(81, copy.value, 0.1d);
        assertEquals(now, copy.date);
        assertEquals(bgReading.directionToSymbol(), copy.directionToSymbol());
    }

    @Test
    public void isEqualTest() {
        bgReading = new BgReading();
        BgReading copy = new BgReading();
        bgReading.value = 81;
        long now = System.currentTimeMillis();
        bgReading.date = now;
        copy.date = now;

        copy.copyFrom(bgReading);

        assertTrue(copy.isEqual(bgReading));
        assertFalse(copy.isEqual(new BgReading()));
    }

    @Test
    public void calculateDirection() {
        List<BgReading> bgReadingsList = null;
        AAPSMocker.mockDatabaseHelper();

        when(MainApp.getDbHelper().getAllBgreadingsDataFromTime(anyLong(),anyBoolean())).thenReturn(bgReadingsList);
        assertEquals("NONE", bgReading.calculateDirection());
        setReadings(72,0);
        assertEquals("DoubleUp", bgReading.calculateDirection());
        setReadings(76,60);
        assertEquals("SingleUp", bgReading.calculateDirection());
        setReadings(74,65);
        assertEquals("FortyFiveUp", bgReading.calculateDirection());
        setReadings(72,72);
        assertEquals("Flat", bgReading.calculateDirection());
        setReadings(0,72);
        assertEquals("DoubleDown", bgReading.calculateDirection());
        setReadings(60,76);
        assertEquals("SingleDown", bgReading.calculateDirection());
        setReadings(65,74);
        assertEquals("FortyFiveDown", bgReading.calculateDirection());
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockDatabaseHelper();
    }

    public void setReadings(int current_value, int previous_value){
        BgReading now = new BgReading();
        now.value = current_value;
        now.date = System.currentTimeMillis();
        BgReading previous = new BgReading();
        previous.value = previous_value;
        previous.date = System.currentTimeMillis() - ( 6 * 60 * 1000L);
        List<BgReading> bgReadings = new ArrayList() {{
            add(now);
            add(previous);
        }};
        when(MainApp.getDbHelper().getAllBgreadingsDataFromTime(anyLong(),anyBoolean())).thenReturn(bgReadings);
    }
}