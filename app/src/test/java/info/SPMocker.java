package info;

import org.junit.Assert;
import org.mockito.ArgumentMatchers;
import org.powermock.api.mockito.PowerMockito;

import java.util.HashMap;

import info.nightscout.androidaps.utils.SP;

public class SPMocker {

    static HashMap<String, Object> data = new HashMap<>();

    public static void prepareMock() {
        PowerMockito.mockStatic(SP.class);

        try {
            PowerMockito.when(SP.class, "putString", ArgumentMatchers.anyString(), ArgumentMatchers.anyString()).then(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                data.put(key, value);
                System.out.print("putString " + key + " " + value + "\n");
                return null;
            });

            PowerMockito.when(SP.class, "getString", ArgumentMatchers.anyString(), ArgumentMatchers.any()).then(invocation -> {
                String key = invocation.getArgument(0);
                String def = invocation.getArgument(1);
                String value = (String) data.get(key);
                if (value == null) value = def;
                System.out.print("getString " + key + " " + value + "\n");
                return value;
            });

            PowerMockito.when(SP.class, "putBoolean", ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean()).then(invocation -> {
                String key = invocation.getArgument(0);
                Boolean value = invocation.getArgument(1);
                data.put(key, value);
                System.out.print("putBoolean " + key + " " + value + "\n");
                return null;
            });

            PowerMockito.when(SP.class, "getBoolean", ArgumentMatchers.anyString(), ArgumentMatchers.any()).then(invocation -> {
                String key = invocation.getArgument(0);
                Boolean def = invocation.getArgument(1);
                Boolean value = (Boolean) data.get(key);
                if (value == null) value = def;
                System.out.print("getBoolean " + key + " " + value + "\n");
                return value;
            });

            PowerMockito.when(SP.class, "getDouble", ArgumentMatchers.anyString(), ArgumentMatchers.any()).then(invocation -> {
                String key = invocation.getArgument(0);
                Double def = invocation.getArgument(1);
                Double value = (Double) data.get(key);
                if (value == null) value = def;
                System.out.print("getDouble " + key + " " + value + "\n");
                return value;
            });

        } catch (Exception e) {
            Assert.fail("Unable to mock the construction of the SP object: " + e.getMessage());
        }

    }

}
