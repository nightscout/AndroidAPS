package info;

import org.junit.Assert;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;

import java.util.HashMap;

import info.nightscout.utils.SP;

import static org.powermock.api.mockito.PowerMockito.when;

public class SPMocker {

    static HashMap<String, Object> data = new HashMap<>();

    public static void prepareMock() {
        PowerMockito.mockStatic(SP.class);

        try {
            PowerMockito.when(SP.class, "putString", ArgumentMatchers.anyString(), ArgumentMatchers.anyString()).then(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                data.put(key,value);
                return null;
            });

            PowerMockito.when(SP.class, "getString", ArgumentMatchers.anyString(), ArgumentMatchers.anyString()).then(invocation -> {
                String key = invocation.getArgument(0);
                String def = invocation.getArgument(1);
                String value = (String) data.get(key);
                if (value == null) value = def;
                return value;
            });
        } catch (Exception e) {
            Assert.fail("Unable to mock the construction of "
                    + "the SP object");
        }

    }

}
