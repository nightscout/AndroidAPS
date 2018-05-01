package com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages;

/**
 * Created by geoff on 5/29/16.
 */
public class MessageType {
    public static final byte Invalid         = (byte)0x00;
    public static final byte Alert           = (byte)0x01;
    public static final byte AlertCleared    = (byte)0x02;
    public static final byte DeviceTest      = (byte)0x03;
    public static final byte PumpStatus      = (byte)0x04;
    public static final byte PumpAck         = (byte)0x06;
    public static final byte PumpBackfill    = (byte)0x08;
    public static final byte FindDevice      = (byte)0x09;
    public static final byte DeviceLink      = (byte)0x0a;
    public static final byte ChangeTime      = (byte)0x40;
    public static final byte Bolus           = (byte)0x42;
    public static final byte ChangeTempBasal = (byte)0x4c;
    public static final byte ButtonPress     = (byte)0x5b;
    public static final byte PowerOn         = (byte)0x5d;
    public static final byte ReadTime        = (byte)0x70;
    public static final byte GetBattery      = (byte)0x72;
    public static final byte GetHistoryPage  = (byte)0x80;
    public static final byte GetISFProfile   = (byte)0x8b;
    public static final byte GetPumpModel    = (byte)0x8d;
    public static final byte ReadTempBasal   = (byte)0x98;
    public static final byte ReadSettings    = (byte)0xc0;

    // The above codes include codes that are not 522/722 specific.
    
    // The codes below here are Medtronic pump specific.
    // from Roundtrip.Carelink:
    public static final byte CMD_M_PACKET_LENGTH                  = ((byte)7);  // 0x07
    public static final byte CMD_M_BEGIN_PARAMETER_SETTING        = ((byte)38); // 0x26
    public static final byte CMD_M_END_PARAMETER_SETTING          = ((byte)39); // 0x27
    public static final byte CMD_M_SET_A_PROFILE                  = ((byte)48); // 0x30
    public static final byte CMD_M_SET_B_PROFILE                  = ((byte)49); // 0x31
    public static final byte CMD_M_SET_LOGIC_LINK_ID              = ((byte)50); // 0x32
    public static final byte CMD_M_SET_LOGIC_LINK_ENABLE          = ((byte)51); // 0x33
    public static final byte CMD_M_SET_RTC                        = ((byte)64); // 0x40
    public static final byte CMD_M_SET_MAX_BOLUS                  = ((byte)65); // 0x41
    public static final byte CMD_M_BOLUS                          = ((byte)66); // 0x42
    public static final byte CMD_M_SET_VAR_BOLUS_ENABLE           = ((byte)69); // 0x45
    public static final byte CMD_M_SET_CURRENT_PATTERN            = ((byte)74); // 0x4a
    public static final byte CMD_M_TEMP_BASAL_RATE                = ((byte)76); // 0x4c
    public static final byte CMD_M_SUSPEND_RESUME                 = ((byte)77); // 0x4d
    public static final byte CMD_M_SET_AUTO_OFF                   = ((byte)78); // 0x4e
    public static final byte CMD_M_SET_EASY_BOLUS_ENABLE          = ((byte)79); // 0x4f
    public static final byte CMD_M_SET_RF_REMOTE_ID               = ((byte)81); // 0x51
    public static final byte CMD_M_SET_BLOCK_ENABLE               = ((byte)82); // 0x52
    public static final byte CMD_M_SET_ALERT_TYPE                 = ((byte)84); // 0x54
    public static final byte CMD_M_SET_PATTERNS_ENABLE            = ((byte)85); // 0x55
    public static final byte CMD_M_SET_RF_ENABLE                  = ((byte)87); // 0x57
    public static final byte CMD_M_SET_INSULIN_ACTION_TYPE        = ((byte)88); // 0x58
    public static final byte CMD_M_KEYPAD_PUSH                    = ((byte)91); // 0x5b
    public static final byte CMD_M_SET_TIME_FORMAT                = ((byte)92); // 0x5c
    public static final byte CMD_M_POWER_CTRL                     = ((byte)93); // 0x5d
    public static final byte CMD_M_SET_BOLUS_WIZARD_SETUP         = ((byte)94); // 0x5e
    public static final byte CMD_M_SET_BG_ALARM_ENABLE            = ((byte)103);// 0x67
    public static final byte CMD_M_SET_TEMP_BASAL_TYPE            = ((byte)104);// 0x68
    public static final byte CMD_M_SET_RESERVOIR_WARNING          = ((byte)106);// 0x6a
    public static final byte CMD_M_SET_BG_ALARM_CLOCKS            = ((byte)107);// 0x6b
    public static final byte CMD_M_SET_BG_REMINDER_ENABLE         = ((byte)108);// 0x6c
    public static final byte CMD_M_SET_MAX_BASAL                  = ((byte)110);// 0x6e
    public static final byte CMD_M_SET_STD_PROFILE                = ((byte)111);// 0x6f
    public static final byte CMD_M_READ_RTC                       = ((byte)112);// 0x70
    public static final byte CMD_M_READ_PUMP_ID                   = ((byte)113);// 0x71
    public static final byte CMD_M_READ_INSULIN_REMAINING         = ((byte)115);// 0x73
    public static final byte CMD_M_READ_FIRMWARE_VER              = ((byte)116);// 0x74
    public static final byte CMD_M_READ_ERROR_STATUS              = ((byte)117);// 0x75
    public static final byte CMD_M_READ_REMOTE_CTRL_IDS           = ((byte)118);// 0x76
    public static final byte CMD_M_READ_HISTORY                   = ((byte)128);// 0x80
    public static final byte CMD_M_READ_PUMP_STATE                = ((byte)131);// 0x83
    public static final byte CMD_M_READ_BOLUS_WIZARD_SETUP_STATUS = ((byte)135);// 0x87
    public static final byte CMD_M_READ_CARB_UNITS                = ((byte)136);// 0x88
    public static final byte CMD_M_READ_BG_UNITS                  = ((byte)137);// 0x89
    public static final byte CMD_M_READ_CARB_RATIOS               = ((byte)138);// 0x8a
    public static final byte CMD_M_READ_INSULIN_SENSITIVITIES     = ((byte)139);// 0x8b
    public static final byte CMD_M_READ_BG_TARGETS                = ((byte)140);// 0x8c
    public static final byte CMD_M_READ_PUMP_MODEL_NUMBER         = ((byte)141);// 0x8d
    public static final byte CMD_M_READ_BG_ALARM_CLOCKS           = ((byte)142);// 0x8e
    public static final byte CMD_M_READ_RESERVOIR_WARNING         = ((byte)143);// 0x8f
    public static final byte CMD_M_READ_BG_REMINDER_ENABLE        = ((byte)144);// 0x90
    public static final byte CMD_M_READ_SETTINGS                  = ((byte)145);// 0x91
    public static final byte CMD_M_READ_STD_PROFILES              = ((byte)146);// 0x92
    public static final byte CMD_M_READ_A_PROFILES                = ((byte)147);// 0x93
    public static final byte CMD_M_READ_B_PROFILES                = ((byte)148);// 0x94
    public static final byte CMD_M_READ_LOGIC_LINK_IDS            = ((byte)149);// 0x95
    public static final byte CMD_M_READ_BG_ALARM_ENABLE           = ((byte)151);// 0x97
    public static final byte CMD_M_READ_TEMP_BASAL                = ((byte)152);// 0x98
    public static final byte CMD_M_READ_PUMP_SETTINGS             = ((byte)192);// 0xc0
    public static final byte CMD_M_READ_PUMP_STATUS               = ((byte)206);// 0xce

    public byte mtype;
    public MessageType(byte mtype) {
        this.mtype = mtype;
    }

    public static MessageBody constructMessageBody(MessageType messageType, byte[] bodyData) {
        switch (messageType.mtype) {
            case PumpAck: return new PumpAckMessageBody(bodyData);
            default: return new UnknownMessageBody(bodyData);
        }
    }
}
