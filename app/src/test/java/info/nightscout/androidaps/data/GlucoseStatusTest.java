package info.nightscout.androidaps.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.T;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by mike on 26.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, DatabaseHelper.class, DateUtil.class})
public class GlucoseStatusTest {

    @Test
    public void toStringShouldBeOverloaded() {
        GlucoseStatus glucoseStatus = new GlucoseStatus();
        Assert.assertEquals(true, glucoseStatus.toString().contains("Delta"));
    }

    @Test
    public void roundtest() {
        GlucoseStatus glucoseStatus = new GlucoseStatus();
        glucoseStatus.glucose = 100.11111;
        Assert.assertEquals(100.1, glucoseStatus.round().glucose, 0.0001);
    }

    @Test
    public void calculateValidGlucoseStatus() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateValidBgData());

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        Assert.assertEquals(214d, glucoseStatus.glucose, 0.001d);
        Assert.assertEquals(-2d, glucoseStatus.delta, 0.001d);
        Assert.assertEquals(-2.5d, glucoseStatus.short_avgdelta, 0.001d); // -2 -2.5 -3 deltas are relative to current value
        Assert.assertEquals(-2.5d, glucoseStatus.avgdelta, 0.001d); // the same as short_avgdelta
        Assert.assertEquals(-2d, glucoseStatus.long_avgdelta, 0.001d); // -2 -2 -2 -2
        Assert.assertEquals(1514766900000L, glucoseStatus.date); // latest date
    }

    @Test
    public void calculateMostRecentGlucoseStatus() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateMostRecentBgData());

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        Assert.assertEquals(215d, glucoseStatus.glucose, 0.001d); // (214+216) / 2
        Assert.assertEquals(-1.25d, glucoseStatus.delta, 0.001d);
        Assert.assertEquals(-1.25d, glucoseStatus.short_avgdelta, 0.001d);
        Assert.assertEquals(-1.25d, glucoseStatus.avgdelta, 0.001d);
        Assert.assertEquals(0d, glucoseStatus.long_avgdelta, 0.001d);
        Assert.assertEquals(1514766850000L, glucoseStatus.date); // date is average too
    }

    @Test
    public void oneRecordShouldProduceZeroDeltas() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateOneCurrentRecordBgData());

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        Assert.assertEquals(214d, glucoseStatus.glucose, 0.001d);
        Assert.assertEquals(0d, glucoseStatus.delta, 0.001d);
        Assert.assertEquals(0d, glucoseStatus.short_avgdelta, 0.001d); // -2 -2.5 -3 deltas are relative to current value
        Assert.assertEquals(0d, glucoseStatus.avgdelta, 0.001d); // the same as short_avgdelta
        Assert.assertEquals(0d, glucoseStatus.long_avgdelta, 0.001d); // -2 -2 -2 -2
        Assert.assertEquals(1514766900000L, glucoseStatus.date); // latest date
    }

    @Test
    public void insuffientDataShouldReturnNull() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateInsufficientBgData());

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        Assert.assertEquals(null, glucoseStatus);
    }

    @Test
    public void oldDataShouldReturnNull() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateOldBgData());

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        Assert.assertEquals(null, glucoseStatus);
    }

    @Test
    public void returnOldDataIfAllowed() {
        when(MainApp.getDbHelper().getBgreadingsDataFromTime(anyLong(), anyBoolean())).thenReturn(generateOldBgData());

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData(true);
        Assert.assertNotEquals(null, glucoseStatus);
    }

    @Test
    public void averageShouldNotFailOnEmptyArray() {
        Assert.assertEquals(0d, GlucoseStatus.average(new ArrayList<>()), 0.001d);
    }

    @Before
    public void initMocking() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
        AAPSMocker.mockDatabaseHelper();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(1514766900000L + T.mins(1).msecs());
    }

    // [{"mgdl":214,"mills":1521895773113,"device":"xDrip-DexcomG5","direction":"Flat","filtered":191040,"unfiltered":205024,"noise":1,"rssi":100},{"mgdl":219,"mills":1521896073352,"device":"xDrip-DexcomG5","direction":"Flat","filtered":200160,"unfiltered":209760,"noise":1,"rssi":100},{"mgdl":222,"mills":1521896372890,"device":"xDrip-DexcomG5","direction":"Flat","filtered":207360,"unfiltered":212512,"noise":1,"rssi":100},{"mgdl":220,"mills":1521896673062,"device":"xDrip-DexcomG5","direction":"Flat","filtered":211488,"unfiltered":210688,"noise":1,"rssi":100},{"mgdl":193,"mills":1521896972933,"device":"xDrip-DexcomG5","direction":"Flat","filtered":212384,"unfiltered":208960,"noise":1,"rssi":100},{"mgdl":181,"mills":1521897273336,"device":"xDrip-DexcomG5","direction":"SingleDown","filtered":210592,"unfiltered":204320,"noise":1,"rssi":100},{"mgdl":176,"mills":1521897572875,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":206720,"unfiltered":197440,"noise":1,"rssi":100},{"mgdl":168,"mills":1521897872929,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":201024,"unfiltered":187904,"noise":1,"rssi":100},{"mgdl":161,"mills":1521898172814,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":193376,"unfiltered":178144,"noise":1,"rssi":100},{"mgdl":148,"mills":1521898472879,"device":"xDrip-DexcomG5","direction":"SingleDown","filtered":183264,"unfiltered":161216,"noise":1,"rssi":100},{"mgdl":139,"mills":1521898772862,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":170784,"unfiltered":148928,"noise":1,"rssi":100},{"mgdl":132,"mills":1521899072896,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":157248,"unfiltered":139552,"noise":1,"rssi":100},{"mgdl":125,"mills":1521899372834,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":144416,"unfiltered":129616.00000000001,"noise":1,"rssi":100},{"mgdl":128,"mills":1521899973456,"device":"xDrip-DexcomG5","direction":"Flat","filtered":130240.00000000001,"unfiltered":133536,"noise":1,"rssi":100},{"mgdl":132,"mills":1521900573287,"device":"xDrip-DexcomG5","direction":"Flat","filtered":133504,"unfiltered":138720,"noise":1,"rssi":100},{"mgdl":127,"mills":1521900873711,"device":"xDrip-DexcomG5","direction":"Flat","filtered":136480,"unfiltered":132992,"noise":1,"rssi":100},{"mgdl":127,"mills":1521901180151,"device":"xDrip-DexcomG5","direction":"Flat","filtered":136896,"unfiltered":132128,"noise":1,"rssi":100},{"mgdl":125,"mills":1521901473582,"device":"xDrip-DexcomG5","direction":"Flat","filtered":134624,"unfiltered":129696,"noise":1,"rssi":100},{"mgdl":120,"mills":1521901773597,"device":"xDrip-DexcomG5","direction":"Flat","filtered":130704.00000000001,"unfiltered":123376,"noise":1,"rssi":100},{"mgdl":116,"mills":1521902075855,"device":"xDrip-DexcomG5","direction":"Flat","filtered":126272,"unfiltered":118448,"noise":1,"rssi":100}]
    List<BgReading> generateValidBgData() {
        List<BgReading> list = new ArrayList<>();
        try {
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":214,\"mills\":1514766900000,\"direction\":\"Flat\"}"))));
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":216,\"mills\":1514766600000,\"direction\":\"Flat\"}")))); // +2
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":219,\"mills\":1514766300000,\"direction\":\"Flat\"}")))); // +3
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":223,\"mills\":1514766000000,\"direction\":\"Flat\"}")))); // +4
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":222,\"mills\":1514765700000,\"direction\":\"Flat\"}"))));
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":224,\"mills\":1514765400000,\"direction\":\"Flat\"}"))));
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":226,\"mills\":1514765100000,\"direction\":\"Flat\"}"))));
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":228,\"mills\":1514764800000,\"direction\":\"Flat\"}"))));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    List<BgReading> generateMostRecentBgData() {
        List<BgReading> list = new ArrayList<>();
        try {
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":214,\"mills\":1514766900000,\"direction\":\"Flat\"}"))));
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":216,\"mills\":1514766800000,\"direction\":\"Flat\"}")))); // +2
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":216,\"mills\":1514766600000,\"direction\":\"Flat\"}"))));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    List<BgReading> generateInsufficientBgData() {
        List<BgReading> list = new ArrayList<>();
        return list;
    }

    List<BgReading> generateOldBgData() {
        List<BgReading> list = new ArrayList<>();
        try {
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":228,\"mills\":1514764800000,\"direction\":\"Flat\"}"))));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    List<BgReading> generateOneCurrentRecordBgData() {
        List<BgReading> list = new ArrayList<>();
        try {
            list.add(new BgReading(new NSSgv(new JSONObject("{\"mgdl\":214,\"mills\":1514766900000,\"direction\":\"Flat\"}"))));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }
}
