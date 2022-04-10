package info.nightscout.androidaps.plugins.pump.insight.utils;

public final class BOCUtil {

    public static int parseBOC(byte b) {
        return ((b & 0xF0) >> 4) * 10 + (b & 0x0F);
    }
}
