package info.nightscout.androidaps.testing.mocks;

import android.content.Intent;
import android.os.Bundle;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public final class IntentMock {

    public static Intent mock() {
        return mock(new HashMap<String, Object>());
    }

    public static Intent mock(final HashMap<String, Object> map) {

        Answer put = invocation -> {
            map.put((String)invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
        Answer<Object> get = invocation -> map.get(invocation.getArguments()[0]);

        Intent intent = Mockito.mock(Intent.class);

        when(intent.putExtra(anyString(), any(Bundle.class))).thenAnswer(put);
        when(intent.getBundleExtra(anyString())).thenAnswer(get);

        doAnswer(invocation -> map.containsKey(invocation.getArguments()[0])).when(intent).hasExtra(anyString());

        return intent;
    }
}