package com.gxwtech.roundtrip2.util;

import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Created by geoff on 4/28/15.
 */
public class StringUtil {

    public static String fromBytes(byte[] ra) {
        return new String(ra, Charset.forName("UTF-8"));
    }

    // these should go in some project-wide string utils package
    public static String join(ArrayList<String> ra, String joiner) {
        int sz = ra.size();
        String rval = "";
        int n;
        for (n = 0; n < sz; n++) {
            rval = rval + ra.get(n);
            if (n < sz - 1) {
                rval = rval + joiner;
            }
        }
        return rval;
    }

    public static String testJoin() {
        ArrayList<String> ra = new ArrayList<String>();
        ra.add("one");
        ra.add("two");
        ra.add("three");
        return join(ra, "+");
    }

}
