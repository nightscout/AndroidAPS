package info.nightscout.androidaps.plugins.pump.danaR.comm;

/**
 * Created by mike on 28.05.2016.
 */
public class RecordTypes {
    public static final byte RECORD_TYPE_BOLUS =      (byte) 0x01;
    public static final byte RECORD_TYPE_DAILY =      (byte) 0x02;
    public static final byte RECORD_TYPE_PRIME =      (byte) 0x03;
    public static final byte RECORD_TYPE_ERROR =      (byte) 0x04;
    public static final byte RECORD_TYPE_ALARM =      (byte) 0x05;
    public static final byte RECORD_TYPE_GLUCOSE =    (byte) 0x06;
    public static final byte RECORD_TYPE_CARBO =      (byte) 0x08;
    public static final byte RECORD_TYPE_REFILL =     (byte) 0x09;
    public static final byte RECORD_TYPE_SUSPEND =    (byte) 0x0B;
    public static final byte RECORD_TYPE_BASALHOUR =  (byte) 0x0C;
    public static final byte RECORD_TYPE_TB =         (byte) 0x0D;
    public static final byte RECORD_TYPE_TEMP_BASAL = (byte) 0x14;
}
