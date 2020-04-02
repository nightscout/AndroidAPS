package info.nightscout.androidaps.testing.mockers;

import android.content.Context;
import android.content.SharedPreferences;

import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.testing.mocks.SharedPreferencesMock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public class AAPSMocker {

    private static final Map<String, SharedPreferences> mockedSharedPrefs = new HashMap<>();

    public static void prepareMock() throws Exception {
        Context mockedContext = mock(Context.class);
        mockStatic(MainApp.class, InvocationOnMock::callRealMethod);

        PowerMockito.when(mockedContext, "getSharedPreferences",  ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()).thenAnswer(invocation -> {

            final String key = invocation.getArgument(0);
            if (mockedSharedPrefs.containsKey(key)) {
                return mockedSharedPrefs.get(key);
            } else {
                SharedPreferencesMock newPrefs = new SharedPreferencesMock();
                mockedSharedPrefs.put(key, newPrefs);
                return newPrefs;
            }
        });

        resetMockedSharedPrefs();
    }

    public static void resetMockedSharedPrefs() {
        mockedSharedPrefs.clear();
    }

    public static File getMockedFile() {
        File file = mock(File.class);
        when(file.exists()).thenReturn(true);
        when(file.canRead()).thenReturn(true);
        when(file.canWrite()).thenReturn(true);
        return file;
    }

}
