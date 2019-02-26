package info.nightscout.androidaps.plugins.pump.insight.satl;

public enum SatlError {

    UNDEFINED,
    INCOMPATIBLE_VERSION,
    INVALID_COMM_ID,
    INVALID_MAC_TRAILER,
    INVALID_CRC,
    INVALID_PACKET,
    INVALID_NONCE,
    DECRYPT_VERIFY_FAILED,
    COMPATIBLE_STATE,
    WRONG_STATE,
    INVALID_MESSAGE_TYPE,
    INVALID_PAYLOAD_LENGTH,
    NONE;
}
