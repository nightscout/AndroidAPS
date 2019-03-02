package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by geoff on 5/29/16.
 */

// changed to Enum, since this is now enum, I had to remove duplicate entries, so if entry is on first list, it was
// removed
// from second (commented out)
@Deprecated
public enum MessageType {
    Invalid((byte)0x00),
    Alert((byte)0x01), //
    AlertCleared((byte)0x02), //
    DeviceTest((byte)0x03), //
    PumpStatus((byte)0x04), //
    PumpAck((byte)0x06), //
    PumpBackfill((byte)0x08), //
    FindDevice((byte)0x09), //
    DeviceLink((byte)0x0a), //
    ChangeTime((byte)0x40), //
    Bolus((byte)0x42), //
    ChangeTempBasal((byte)0x4c), //
    ButtonPress((byte)0x5b), //
    PowerOn((byte)0x5d), //
    ReadTime((byte)0x70), //
    GetBattery((byte)0x72), //
    GetHistoryPage((byte)0x80), //
    GetISFProfile((byte)0x8b), //
    GetPumpModel((byte)0x8d), //
    ReadTempBasal((byte)0x98), //
    ReadSettings((byte)0xc0), //
    SetBasalProfileSTD((byte)0x6f), //
    ReadBasalProfileSTD((byte)0x92),

    // The above codes include codes that are not 522/722 specific.

    // The codes below here are Medtronic pump specific.
    // from Roundtrip.Carelink:
    CMD_M_PACKET_LENGTH((byte)7), // 0x07
    CMD_M_BEGIN_PARAMETER_SETTING((byte)38), // 0x26
    CMD_M_END_PARAMETER_SETTING((byte)39), // 0x27
    CMD_M_SET_A_PROFILE((byte)48), // 0x30
    CMD_M_SET_B_PROFILE((byte)49), // 0x31
    CMD_M_SET_LOGIC_LINK_ID((byte)50), // 0x32
    CMD_M_SET_LOGIC_LINK_ENABLE((byte)51), // 0x33
    // CMD_M_SET_RTC ((byte)64), // 0x40 - ChangeTime
    CMD_M_SET_MAX_BOLUS((byte)65), // 0x41
    // CMD_M_BOLUS ((byte)66), // 0x42 - Bolus
    CMD_M_SET_VAR_BOLUS_ENABLE((byte)69), // 0x45
    CMD_M_SET_CURRENT_PATTERN((byte)74), // 0x4a
    // CMD_M_TEMP_BASAL_RATE ((byte)76), // 0x4c - ChangeTempBasal
    CMD_M_SUSPEND_RESUME((byte)77), // 0x4d
    CMD_M_SET_AUTO_OFF((byte)78), // 0x4e
    CMD_M_SET_EASY_BOLUS_ENABLE((byte)79), // 0x4f
    CMD_M_SET_RF_REMOTE_ID((byte)81), // 0x51
    CMD_M_SET_BLOCK_ENABLE((byte)82), // 0x52
    CMD_M_SET_ALERT_TYPE((byte)84), // 0x54
    CMD_M_SET_PATTERNS_ENABLE((byte)85), // 0x55
    CMD_M_SET_RF_ENABLE((byte)87), // 0x57
    CMD_M_SET_INSULIN_ACTION_TYPE((byte)88), // 0x58
    // CMD_M_KEYPAD_PUSH ((byte)91), // 0x5b - ButtonPress
    CMD_M_SET_TIME_FORMAT((byte)92), // 0x5c
    // CMD_M_POWER_CTRL ((byte)93), // 0x5d - PowerOn
    CMD_M_SET_BOLUS_WIZARD_SETUP((byte)94), // 0x5e
    CMD_M_SET_BG_ALARM_ENABLE((byte)103), // 0x67
    CMD_M_SET_TEMP_BASAL_TYPE((byte)104), // 0x68
    CMD_M_SET_RESERVOIR_WARNING((byte)106), // 0x6a
    CMD_M_SET_BG_ALARM_CLOCKS((byte)107), // 0x6b
    CMD_M_SET_BG_REMINDER_ENABLE((byte)108), // 0x6c
    CMD_M_SET_MAX_BASAL((byte)110), // 0x6e
    // CMD_M_SET_STD_PROFILE ((byte)111), // 0x6f - SetBasalProfileSTD
    // CMD_M_READ_RTC ((byte)112), // 0x70 - ReadTime
    CMD_M_READ_PUMP_ID((byte)113), // 0x71
    CMD_M_READ_INSULIN_REMAINING((byte)115), // 0x73
    CMD_M_READ_FIRMWARE_VER((byte)116), // 0x74
    CMD_M_READ_ERROR_STATUS((byte)117), // 0x75
    CMD_M_READ_REMOTE_CTRL_IDS((byte)118), // 0x76
    // CMD_M_READ_HISTORY ((byte)128), // 0x80 - GetHistoryPage
    CMD_M_READ_PUMP_STATE((byte)131), // 0x83
    CMD_M_READ_BOLUS_WIZARD_SETUP_STATUS((byte)135), // 0x87
    CMD_M_READ_CARB_UNITS((byte)136), // 0x88
    CMD_M_READ_BG_UNITS((byte)137), // 0x89
    CMD_M_READ_CARB_RATIOS((byte)138), // 0x8a
    // CMD_M_READ_INSULIN_SENSITIVITIES ((byte)139), // 0x8b - GetISFProfile
    CMD_M_READ_BG_TARGETS((byte)140), // 0x8c
    // CMD_M_READ_PUMP_MODEL_NUMBER ((byte)141), // 0x8d - GetPumpModel
    CMD_M_READ_BG_ALARM_CLOCKS((byte)142), // 0x8e
    CMD_M_READ_RESERVOIR_WARNING((byte)143), // 0x8f
    CMD_M_READ_BG_REMINDER_ENABLE((byte)144), // 0x90
    CMD_M_READ_SETTINGS((byte)145), // 0x91
    // CMD_M_READ_STD_PROFILES((byte) 146), // 0x92 - ReadBasalProfileSTD
    CMD_M_READ_A_PROFILES((byte)147), // 0x93
    CMD_M_READ_B_PROFILES((byte)148), // 0x94
    CMD_M_READ_LOGIC_LINK_IDS((byte)149), // 0x95
    CMD_M_READ_BG_ALARM_ENABLE((byte)151), // 0x97
    // CMD_M_READ_TEMP_BASAL ((byte)152), // 0x98 - ReadTempBasal
    // CMD_M_READ_PUMP_SETTINGS ((byte)192), // 0xc0 - ReadSettings
    CMD_M_READ_PUMP_STATUS((byte)206), // 0xce

    ;

    private static final Logger LOG = LoggerFactory.getLogger(MessageType.class);

    static Map<Byte, MessageType> mapByValue;

    static {
        mapByValue = new HashMap<>();

        boolean foundErrors = false;

        for (MessageType messageType : values()) {

            if (mapByValue.containsKey(messageType.getValue())) {
                // leave this check in case someone adds any new commands
                LOG.error("Duplicate entries: {}, {} ", mapByValue.get(messageType.getValue()), messageType);
                foundErrors = true;
            } else {
                mapByValue.put(messageType.getValue(), messageType);
            }
        }

        if (foundErrors) {
            LOG.error("MessageType has duplicate entries. Each items needs to have unique value associated with it. "
                + "If this is not the case, application might not work correctly. Fix this and restart application.");

            System.exit(0);
        }

    }

    byte mtype;


    MessageType(byte mtype) {
        this.mtype = mtype;
    }


    public static MessageBody constructMessageBody(MessageType messageType, byte[] bodyData) {
        switch (messageType) {
            case PumpAck:
                return new PumpAckMessageBody(bodyData);
            default:
                return new UnknownMessageBody(bodyData);
        }
    }


    public static MessageType getByValue(byte msgType) {

        if (mapByValue.containsKey(msgType)) {
            return mapByValue.get(msgType);
        } else {
            return MessageType.Invalid;
        }
    }


    public byte getValue() {
        return this.mtype;
    }
}
