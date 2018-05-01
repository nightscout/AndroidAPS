package com.gxwtech.roundtrip2.RoundtripService.medtronic;
// cribbed from:
//package com.nightscout.core.drivers.Medtronic;

/**
 * Created by geoff on 5/13/15.
 */

public enum PumpModel {
    UNSET,
    MM508,
    MM515,
    MM522,
    MM523;
    public static boolean isLargerFormat(PumpModel model) {
        if (model == MM523) {
            return true;
        }
        return false;
    }
    public static String toString(PumpModel model) {
        switch(model) {
            case UNSET:
                return "UNSET";
            case MM508:
                return "508";
            case MM515:
                return "515";
            case MM522:
                return "522";
            case MM523:
                return "523";
            default:
                return "(error)";
        }
    }
    public static PumpModel fromString(String s) {
        if ("UNSET".equals(s)) {
            return UNSET;
        }
        if (("508".equals(s)) || ("MM508".equals(s))) {
            return MM508;
        }
        if (("515".equals(s)) || ("MM515".equals(s))) {
            return MM515;
        }
        if (("522".equals(s)) || ("MM522".equals(s))) {
            return MM522;
        }
        if (("523".equals(s)) || ("MM523".equals(s))) {
            return MM523;
        }
        return UNSET;
    }
}