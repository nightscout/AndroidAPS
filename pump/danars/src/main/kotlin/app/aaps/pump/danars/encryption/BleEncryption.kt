@file:Suppress("unused", "PackageDirectoryMismatch")

package info.nightscout.androidaps.danars.encryption

import android.content.Context
import app.aaps.pump.danars.encryption.EncryptionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleEncryption @Inject constructor(private val context: Context) {

    fun getEncryptedPacket(opcode: Int, bytes: ByteArray?, deviceName: String?): ByteArray =
        encryptPacketJni(context, opcode, bytes, deviceName)

    fun getDecryptedPacket(bytes: ByteArray): ByteArray? =
        decryptPacketJni(context, bytes)

    fun setPairingKeys(pairingKey: ByteArray, randomPairingKey: ByteArray, randomSyncKey: Byte) {
        setPairingKeysJni(pairingKey, randomPairingKey, randomSyncKey)
    }

    fun setBle5Key(ble5Key: ByteArray) {
        setBle5KeyJni(ble5Key)
    }

    fun setEnhancedEncryption(securityVersion: EncryptionType) {
        setEnhancedEncryptionJni(securityVersion.ordinal)
    }

    fun encryptSecondLevelPacket(bytes: ByteArray): ByteArray =
        encryptSecondLevelPacketJni(context, bytes)

    fun decryptSecondLevelPacket(bytes: ByteArray): ByteArray =
        decryptSecondLevelPacketJni(context, bytes)

    companion object {

        const val DANAR_PACKET__TYPE_ENCRYPTION_REQUEST = 0x01
        const val DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE = 0x02
        const val DANAR_PACKET__TYPE_COMMAND = 0xA1
        const val DANAR_PACKET__TYPE_RESPONSE = 0xB2
        const val DANAR_PACKET__TYPE_NOTIFY = 0xC3
        const val DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK = 0x00
        const val DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION = 0x01
        const val DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY = 0xD0
        const val DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST = 0xD1
        const val DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN = 0xD2

        // Easy Mode
        const val DANAR_PACKET__OPCODE_ENCRYPTION__GET_PUMP_CHECK = 0xF3
        const val DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK = 0xF4
        const val DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE = 0x01
        const val DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY = 0x02
        const val DANAR_PACKET__OPCODE_NOTIFY__ALARM = 0x03
        const val DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM = 0x04
        const val DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION = 0x02
        const val DANAR_PACKET__OPCODE_REVIEW__DELIVERY_STATUS = 0x03
        const val DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD = 0x04
        const val DANAR_PACKET__OPCODE_REVIEW__BOLUS_AVG = 0x10
        const val DANAR_PACKET__OPCODE_REVIEW__BOLUS = 0x11
        const val DANAR_PACKET__OPCODE_REVIEW__DAILY = 0x12
        const val DANAR_PACKET__OPCODE_REVIEW__PRIME = 0x13
        const val DANAR_PACKET__OPCODE_REVIEW__REFILL = 0x14
        const val DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE = 0x15
        const val DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE = 0x16
        const val DANAR_PACKET__OPCODE_REVIEW__TEMPORARY = 0x17
        const val DANAR_PACKET__OPCODE_REVIEW__SUSPEND = 0x18
        const val DANAR_PACKET__OPCODE_REVIEW__ALARM = 0x19
        const val DANAR_PACKET__OPCODE_REVIEW__BASAL = 0x1A
        const val DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY = 0x1F
        const val DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION = 0x20
        const val DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK = 0x21
        const val DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG = 0x22
        const val DANAR_PACKET__OPCODE_REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR = 0x23
        const val DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION = 0x24
        const val DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE = 0x25
        const val DANAR_PACKET__OPCODE_REVIEW__GET_TODAY_DELIVERY_TOTAL = 0x26
        const val DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION = 0x40
        const val DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE = 0x41
        const val DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS = 0x42
        const val DANAR_PACKET__OPCODE_BOLUS__GET_DUAL_BOLUS = 0x43
        const val DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP = 0x44
        const val DANAR_PACKET__OPCODE_BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION = 0x45
        const val DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_MENU_OPTION_STATE = 0x46
        const val DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS = 0x47
        const val DANAR_PACKET__OPCODE_BOLUS__SET_DUAL_BOLUS = 0x48
        const val DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL = 0x49
        const val DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START = 0x4A
        const val DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION = 0x4B
        const val DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE = 0x4C
        const val DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_RATE = 0x4D
        const val DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY = 0x4E
        const val DANAR_PACKET__OPCODE_BOLUS__SET_CIR_CF_ARRAY = 0x4F
        const val DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION = 0x50
        const val DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_OPTION = 0x51
        const val DANAR_PACKET__OPCODE_BOLUS__GET_24_CIR_CF_ARRAY = 0x52
        const val DANAR_PACKET__OPCODE_BOLUS__SET_24_CIR_CF_ARRAY = 0x53
        const val DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL = 0x60
        const val DANAR_PACKET__OPCODE_BASAL__TEMPORARY_BASAL_STATE = 0x61
        const val DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL = 0x62
        const val DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER = 0x63
        const val DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER = 0x64
        const val DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE = 0x65
        const val DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE = 0x66
        const val DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE = 0x67
        const val DANAR_PACKET__OPCODE_BASAL__SET_BASAL_RATE = 0x68
        const val DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_ON = 0x69
        const val DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF = 0x6A
        const val DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME = 0x70
        const val DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME = 0x71
        const val DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION = 0x72
        const val DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION = 0x73
        const val DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL = 0xC1
        const val DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS = 0xC2
        const val DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY = 0xC3

        // v3 specific
        const val DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_DEC_RATIO = 0x80
        const val DANAR_PACKET__OPCODE_GENERAL__GET_SHIPPING_VERSION = 0x81

        // Easy Mode
        const val DANAR_PACKET__OPCODE_OPTION__GET_EASY_MENU_OPTION = 0x74
        const val DANAR_PACKET__OPCODE_OPTION__SET_EASY_MENU_OPTION = 0x75
        const val DANAR_PACKET__OPCODE_OPTION__GET_EASY_MENU_STATUS = 0x76
        const val DANAR_PACKET__OPCODE_OPTION__SET_EASY_MENU_STATUS = 0x77
        const val DANAR_PACKET__OPCODE_OPTION__GET_PUMP_UTC_AND_TIME_ZONE = 0x78
        const val DANAR_PACKET__OPCODE_OPTION__SET_PUMP_UTC_AND_TIME_ZONE = 0x79
        const val DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME_ZONE = 0x7A
        const val DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME_ZONE = 0x7B
        const val DANAR_PACKET__OPCODE_ETC__SET_HISTORY_SAVE = 0xE0
        const val DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION = 0xFF

        init {
            System.loadLibrary("BleEncryption")
        }
    }

    private external fun encryptPacketJni(context: Any, opcode: Int, bytes: ByteArray?, deviceName: String?): ByteArray
    private external fun decryptPacketJni(context: Any, bytes: ByteArray): ByteArray?
    private external fun setPairingKeysJni(pairingKey: ByteArray, randomPairingKey: ByteArray, randomSyncKey: Byte)
    private external fun setBle5KeyJni(ble5Key: ByteArray)
    private external fun setEnhancedEncryptionJni(securityVersion: Int)
    private external fun encryptSecondLevelPacketJni(context: Any, bytes: ByteArray): ByteArray
    private external fun decryptSecondLevelPacketJni(context: Any, bytes: ByteArray): ByteArray
}
