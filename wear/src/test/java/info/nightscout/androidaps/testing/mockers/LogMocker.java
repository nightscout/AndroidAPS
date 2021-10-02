package info.nightscout.androidaps.testing.mockers;

import static org.powermock.api.mockito.PowerMockito.mockStatic;

import android.util.Log;

public class LogMocker {
    public static void prepareMock() {
        mockStatic(Log.class);
    }
}
