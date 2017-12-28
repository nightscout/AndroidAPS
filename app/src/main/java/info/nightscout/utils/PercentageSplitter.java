package info.nightscout.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mike on 22.12.2017.
 */

public class PercentageSplitter {
    public static String pureName(String name) {
        String newName = name;
        String s = "(.*)\\((\\d+)\\%\\)";
        Pattern r = Pattern.compile(s);
        Matcher m = r.matcher(name);
        if (m.find()) {
            newName = m.group(1);
        }
        return newName;
    }
}
