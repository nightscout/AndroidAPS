package info.nightscout.androidaps.testing.mockers;

import android.os.Bundle;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;

import org.junit.Assert;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;

import java.util.ArrayList;

import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.WearUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public class WearUtilMocker {

    public static final long REF_NOW = 1572610530000L;
    private static long clockMsDiff = 0L;

    public static void prepareMock() throws Exception {
        resetClock();
        mockStatic(WearUtil.class, InvocationOnMock::callRealMethod);

        // because we cleverly used timestamp() by implementation, we can mock it
        // and control the time in tests
        PowerMockito.when(WearUtil.class, "timestamp").then(invocation -> (REF_NOW + clockMsDiff));
    }

    public static void prepareMockNoReal() throws Exception {
        resetClock();
        mockStatic(WearUtil.class);

        PowerMockito.when(WearUtil.class, "timestamp").then(invocation -> REF_NOW + clockMsDiff);
        PowerMockito.when(WearUtil.class, "getWakeLock", anyString(), anyInt()).then(invocation -> null);
        PowerMockito.when(WearUtil.class, "bundleToDataMap", any(Bundle.class)).then(bundleToDataMapMock);
    }

    public static void resetClock() {
        clockMsDiff = 0L;
    }

    public static void progressClock(long byMilliseconds) {
        clockMsDiff = clockMsDiff + byMilliseconds;
    }

    public static void setClock(long atMillisecondsSinceEpoch) {
        clockMsDiff = atMillisecondsSinceEpoch - REF_NOW;
    }

    public static long backInTime(int d, int h, int m, int s) {
        return REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s);
    }

    private static Answer bundleToDataMapMock = invocation -> {
        DataMap map = new DataMap();
        Bundle bundle = invocation.getArgument(0);
        for(String key: bundle.keySet()) {
            Object v = bundle.get(key);
            if (v instanceof Asset)     map.putAsset(key, (Asset)v);
            if (v instanceof Boolean)   map.putBoolean(key, (Boolean)v);
            if (v instanceof Byte)      map.putByte(key, (Byte)v);
            if (v instanceof byte[])    map.putByteArray(key, (byte[])v);
            if (v instanceof DataMap)   map.putDataMap(key, (DataMap)v);
            if (v instanceof Double)    map.putDouble(key, (Double)v);
            if (v instanceof Float)     map.putFloat(key, (Float)v);
            if (v instanceof float[])   map.putFloatArray(key, (float[])v);
            if (v instanceof Integer)   map.putInt(key, (Integer)v);
            if (v instanceof Long)      map.putLong(key, (Long)v);
            if (v instanceof long[])    map.putLongArray(key, (long[])v);
            if (v instanceof String)    map.putString(key, (String)v);
            if (v instanceof String[])  map.putStringArray(key, (String[])v);

            if (v instanceof ArrayList) {
                if (!((ArrayList)v).isEmpty()) {
                    if (((ArrayList) v).get(0) instanceof Integer) {
                        map.putIntegerArrayList(key, (ArrayList<Integer>)v);
                    }
                    if (((ArrayList) v).get(0) instanceof String) {
                        map.putStringArrayList(key, (ArrayList<String>)v);
                    }
                    if (((ArrayList) v).get(0) instanceof DataMap) {
                        map.putDataMapArrayList(key, (ArrayList<DataMap>)v);
                    }
                }
            }
        }

        return map;
    };
}
