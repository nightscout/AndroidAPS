package info.nightscout.androidaps.testing.mockers;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.testing.mocks.SharedPreferencesMock;

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public class AAPSMocker {

    private static final Map<String, SharedPreferences> mockedSharedPrefs = new HashMap<>();
    private static boolean unicodeComplicationsOn = true;

    public static void prepareMock() throws Exception {
        Context mockedContext = mock(Context.class);
        mockStatic(aaps.class, InvocationOnMock::callRealMethod);

        PowerMockito.when(aaps.class, "getAppContext").thenReturn(mockedContext);
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
        PowerMockito.when(aaps.class, "areComplicationsUnicode").thenAnswer(invocation -> unicodeComplicationsOn);

        setMockedUnicodeComplicationsOn(true);
        resetMockedSharedPrefs();
    }

    public static void resetMockedSharedPrefs() {
        mockedSharedPrefs.clear();
    }

    public static void resetMockedSharedPrefs(String forKey) {
        mockedSharedPrefs.remove(forKey);
    }

    public static void setMockedUnicodeComplicationsOn(boolean setUnicodeOn) {
        unicodeComplicationsOn = setUnicodeOn;
    }
}
