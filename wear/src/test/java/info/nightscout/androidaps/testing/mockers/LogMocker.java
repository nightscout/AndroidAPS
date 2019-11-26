package info.nightscout.androidaps.testing.mockers;

import android.util.Log;

import static org.powermock.api.mockito.PowerMockito.mockStatic;

public class LogMocker {
    public static void prepareMock() {
        mockStatic(Log.class);
    }
}
