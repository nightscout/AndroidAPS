package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.BLECommOperations;

/**
 * Created by geoff on 5/26/16.
 */
public class BLECommOperationResult {
    public byte[] value;
    public int resultCode;

    public static final int RESULT_NONE = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_TIMEOUT = 2;
    public static final int RESULT_BUSY = 3;
    public static final int RESULT_INTERRUPTED = 4;
    public static final int RESULT_NOT_CONFIGURED = 5;
}
