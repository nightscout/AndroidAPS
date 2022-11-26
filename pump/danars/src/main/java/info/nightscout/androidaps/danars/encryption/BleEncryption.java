package info.nightscout.androidaps.danars.encryption;

import android.content.Context;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.pump.danars.encryption.EncryptionType;

@Singleton
public class BleEncryption {
    private final Context context;

    @Inject BleEncryption(Context context) {
        this.context = context;
    }

    public static final int DANAR_PACKET__TYPE_ENCRYPTION_REQUEST = 0x01;
    public static final int DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE = 0x02;
    public static final int DANAR_PACKET__TYPE_COMMAND = 0xA1;
    public static final int DANAR_PACKET__TYPE_RESPONSE = 0xB2;
    public static final int DANAR_PACKET__TYPE_NOTIFY = 0xC3;

    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK = 0x00;
    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION = 0x01;
    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY = 0xD0;
    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST = 0xD1;
    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN = 0xD2;
    // Easy Mode
    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__GET_PUMP_CHECK = 0xF3;
    public static final int DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASYMENU_CHECK = 0xF4;

    public static final int DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE = 0x01;
    public static final int DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY = 0x02;
    public static final int DANAR_PACKET__OPCODE_NOTIFY__ALARM = 0x03;
    public static final int DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM = 0x04;

    public static final int DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION = 0x02;
    public static final int DANAR_PACKET__OPCODE_REVIEW__DELIVERY_STATUS = 0x03;
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD = 0x04;

    public static final int DANAR_PACKET__OPCODE_REVIEW__BOLUS_AVG = 0x10;
    public static final int DANAR_PACKET__OPCODE_REVIEW__BOLUS = 0x11;
    public static final int DANAR_PACKET__OPCODE_REVIEW__DAILY = 0x12;
    public static final int DANAR_PACKET__OPCODE_REVIEW__PRIME = 0x13;
    public static final int DANAR_PACKET__OPCODE_REVIEW__REFILL = 0x14;
    public static final int DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE = 0x15;
    public static final int DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE = 0x16;
    public static final int DANAR_PACKET__OPCODE_REVIEW__TEMPORARY = 0x17;
    public static final int DANAR_PACKET__OPCODE_REVIEW__SUSPEND = 0x18;
    public static final int DANAR_PACKET__OPCODE_REVIEW__ALARM = 0x19;
    public static final int DANAR_PACKET__OPCODE_REVIEW__BASAL = 0x1A;
    public static final int DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY = 0x1F;
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION = 0x20;
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK = 0x21;
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG = 0x22;
    public static final int DANAR_PACKET__OPCODE_REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR = 0x23;
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION = 0x24;
    public static final int DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE = 0x25;
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_TODAY_DELIVERY_TOTAL = 0x26;

    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION = 0x40;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE = 0x41;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS = 0x42;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_DUAL_BOLUS = 0x43;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP = 0x44;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION = 0x45;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_MENU_OPTION_STATE = 0x46;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS = 0x47;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_DUAL_BOLUS = 0x48;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL = 0x49;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START = 0x4A;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION = 0x4B;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE = 0x4C;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_RATE = 0x4D;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY = 0x4E;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_CIR_CF_ARRAY = 0x4F;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION = 0x50;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_OPTION = 0x51;
    public static final int DANAR_PACKET__OPCODE_BOLUS__GET_24_CIR_CF_ARRAY = 0x52;
    public static final int DANAR_PACKET__OPCODE_BOLUS__SET_24_CIR_CF_ARRAY = 0x53;

    public static final int DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL = 0x60;
    public static final int DANAR_PACKET__OPCODE_BASAL__TEMPORARY_BASAL_STATE = 0x61;
    public static final int DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL = 0x62;
    public static final int DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER = 0x63;
    public static final int DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER = 0x64;
    public static final int DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE = 0x65;
    public static final int DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE = 0x66;
    public static final int DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE = 0x67;
    public static final int DANAR_PACKET__OPCODE_BASAL__SET_BASAL_RATE = 0x68;
    public static final int DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_ON = 0x69;
    public static final int DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF = 0x6A;

    public static final int DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME = 0x70;
    public static final int DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME = 0x71;
    public static final int DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION = 0x72;
    public static final int DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION = 0x73;

    public static final int DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL = 0xC1;
    public static final int DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS = 0xC2;
    public static final int DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY = 0xC3;

    // v3 specific
    public static final int DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_DEC_RATIO = 0x80;
    public static final int DANAR_PACKET__OPCODE_GENERAL__GET_SHIPPING_VERSION = 0x81;

    // Easy Mode
    public static final int DANAR_PACKET__OPCODE_OPTION__GET_EASY_MENU_OPTION = 0x74;
    public static final int DANAR_PACKET__OPCODE_OPTION__SET_EASY_MENU_OPTION = 0x75;
    public static final int DANAR_PACKET__OPCODE_OPTION__GET_EASY_MENU_STATUS = 0x76;
    public static final int DANAR_PACKET__OPCODE_OPTION__SET_EASY_MENU_STATUS = 0x77;
    public static final int DANAR_PACKET__OPCODE_OPTION__GET_PUMP_UTC_AND_TIME_ZONE = 0x78;
    public static final int DANAR_PACKET__OPCODE_OPTION__SET_PUMP_UTC_AND_TIME_ZONE = 0x79;
    public static final int DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME_ZONE = 0x7A;
    public static final int DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME_ZONE = 0x7B;

    public static final int DANAR_PACKET__OPCODE_ETC__SET_HISTORY_SAVE = 0xE0;
    public static final int DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION = 0xFF;

    static {
        System.loadLibrary("BleEncryption");
    }

    private static native byte[] encryptPacketJni(Object context, int opcode, byte[] bytes, String deviceName);

    private static native byte[] decryptPacketJni(Object context, byte[] bytes);

    private static native void setPairingKeysJni(byte[] pairingKey, byte[] randomPairingKey, byte randomSyncKey);

    private static native void setBle5KeyJni(byte[] ble5Key);

    private static native void setEnhancedEncryptionJni(int securityVersion);

    private static native byte[] encryptSecondLevelPacketJni(Object context, byte[] bytes);

    private static native byte[] decryptSecondLevelPacketJni(Object context, byte[] bytes);

    public byte[] getEncryptedPacket(int opcode, byte[] bytes, String deviceName) {
        return encryptPacketJni(context, opcode, bytes, deviceName);
    }

    public byte[] getDecryptedPacket(byte[] bytes) {
        return decryptPacketJni(context, bytes);
    }

    public void setPairingKeys(byte[] pairingKey, byte[] randomPairingKey, byte randomSyncKey) {
        setPairingKeysJni(pairingKey, randomPairingKey, randomSyncKey);
    }

    public void setBle5Key(byte[] ble5Key) {
        setBle5KeyJni(ble5Key);
    }

    public void setEnhancedEncryption(EncryptionType securityVersion) {
        setEnhancedEncryptionJni(securityVersion.ordinal());
    }

    public byte[] encryptSecondLevelPacket(byte[] bytes) {
        return encryptSecondLevelPacketJni(context, bytes);
    }

    public byte[] decryptSecondLevelPacket(byte[] bytes) {
        return decryptSecondLevelPacketJni(context, bytes);
    }
}
