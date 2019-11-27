package info.nightscout.androidaps.testing.mockers;

import org.junit.Assert;
import org.powermock.api.mockito.PowerMockito;

import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public class AndroidMocker {

    public static void mockBase64() throws Exception {
        mockStatic(android.util.Base64.class);

        PowerMockito.when(android.util.Base64.class, "decode", anyString(), anyInt()).thenAnswer(invocation -> {

            final String payload = invocation.getArgument(0);
            try {
                return Base64.getDecoder().decode(payload);
            } catch (java.lang.IllegalArgumentException ex) {
                return null;
            }
        });

        PowerMockito.when(android.util.Base64.class, "encodeToString", any(), anyInt()).thenAnswer(invocation -> {

            final byte[] payload = invocation.getArgument(0);
            return  Base64.getEncoder().encodeToString(payload);

        });
    }

}
