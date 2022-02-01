package info.nightscout.androidaps.testing.utils;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ExtUtil {

    static <T> void assertClassHaveSameFields(Class<T> checkedClass, String commaSeparatedFieldList) {
        Set<String> parentFields = new HashSet<>();
        for (Field f : checkedClass.getDeclaredFields()) {
            final String fieldName = f.getName();
            // skip runtime-injected fields like $jacocoData
            if (fieldName.startsWith("$")) {
                continue;
            }
            parentFields.add(fieldName);
        }

        Set<String> knownFields = new HashSet<>(Arrays.asList(commaSeparatedFieldList.split(",")));
        assertEquals(parentFields, knownFields);
    }

}
