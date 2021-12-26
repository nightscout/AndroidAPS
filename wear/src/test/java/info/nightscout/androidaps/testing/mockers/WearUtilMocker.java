package info.nightscout.androidaps.testing.mockers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.os.Bundle;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;

import info.nightscout.androidaps.interaction.utils.Constants;
import info.nightscout.androidaps.interaction.utils.WearUtil;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WearUtilMocker {

    private final WearUtil wearUtil;

    public WearUtilMocker(WearUtil wearUtil) {
        this.wearUtil = wearUtil;
    }

    public static final long REF_NOW = 1572610530000L;
    private long clockMsDiff = 0L;

    public void prepareMock() {
        resetClock();

        // because we cleverly used timestamp() by implementation, we can mock it
        // and control the time in tests
        when(wearUtil.timestamp()).thenReturn(REF_NOW + clockMsDiff);
    }

    public void prepareMockNoReal() {
        resetClock();

        Mockito.doAnswer(invocation -> REF_NOW + clockMsDiff).when(wearUtil).timestamp();
        Mockito.doReturn(null).when(wearUtil).getWakeLock(anyString(), anyInt());
        Mockito.doAnswer(bundleToDataMapMock).when(wearUtil).bundleToDataMap(any());
    }

    public void resetClock() {
        clockMsDiff = 0L;
    }

    public void progressClock(long byMilliseconds) {
        clockMsDiff = clockMsDiff + byMilliseconds;
    }

    @SuppressWarnings("unused")
    public void setClock(long atMillisecondsSinceEpoch) {
        clockMsDiff = atMillisecondsSinceEpoch - REF_NOW;
    }

    public long backInTime(int d, int h, int m, int s) {
        return REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s);
    }

    private final Answer bundleToDataMapMock = invocation -> {
        DataMap map = new DataMap();
        Bundle bundle = invocation.getArgument(0);
        for (String key : bundle.keySet()) {
            Object v = bundle.get(key);
            if (v instanceof Asset) map.putAsset(key, (Asset) v);
            if (v instanceof Boolean) map.putBoolean(key, (Boolean) v);
            if (v instanceof Byte) map.putByte(key, (Byte) v);
            if (v instanceof byte[]) map.putByteArray(key, (byte[]) v);
            if (v instanceof DataMap) map.putDataMap(key, (DataMap) v);
            if (v instanceof Double) map.putDouble(key, (Double) v);
            if (v instanceof Float) map.putFloat(key, (Float) v);
            if (v instanceof float[]) map.putFloatArray(key, (float[]) v);
            if (v instanceof Integer) map.putInt(key, (Integer) v);
            if (v instanceof Long) map.putLong(key, (Long) v);
            if (v instanceof long[]) map.putLongArray(key, (long[]) v);
            if (v instanceof String) map.putString(key, (String) v);
            if (v instanceof String[]) map.putStringArray(key, (String[]) v);

            if (v instanceof ArrayList) {
                if (!((ArrayList) v).isEmpty()) {
                    if (((ArrayList) v).get(0) instanceof Integer) {
                        map.putIntegerArrayList(key, (ArrayList<Integer>) v);
                    }
                    if (((ArrayList) v).get(0) instanceof String) {
                        map.putStringArrayList(key, (ArrayList<String>) v);
                    }
                    if (((ArrayList) v).get(0) instanceof DataMap) {
                        map.putDataMapArrayList(key, (ArrayList<DataMap>) v);
                    }
                }
            }
        }

        return map;
    };
}
