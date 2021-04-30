package info.nightscout.androidaps.plugins.pump.medtronic.defs

import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.MessageBody
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PumpAckMessageBody
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.UnknownMessageBody
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType.Companion.isSameDevice
import java.util.*

/**
 * Taken from GNU Gluco Control diabetes management software (ggc.sourceforge.net)
 *
 *
 * Description: Medtronic Commands (Pump and CGMS) for all 512 and later models (just 5xx)
 *
 *
 * Link to original/unmodified file:
 * https://sourceforge.net/p/ggc/code/HEAD/tree/trunk/ggc-plugins/ggc-plugins-base/src/
 * main/java/ggc/plugin/device/impl/minimed/enums/MinimedCommandType.java
 *
 *
 * A lot of stuff has been removed because it is not needed anymore (historical stuff from CareLink
 * and Carelink USB communication.
 *
 *
 * Author: Andy {andy@atech-software.com}
 */
enum class MedtronicCommandType
{

    InvalidCommand(0, "Invalid Command", null, null),  //

    // Pump Responses (9)
    CommandACK(0x06, "ACK - Acknowledge", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters),  //
    CommandNAK(0x15, "NAK - Not Acknowledged", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters),  //

    // All (8)
    PushAck(91, "Push ACK", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, byteArrayOf(2)),  //
    PushEsc(91, "Push Esc", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, byteArrayOf(1)),  //
    PushButton(0x5b, "Push Button", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters),  // 91
    RFPowerOn(93, "RF Power On", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, byteArrayOf(1, 10)),  //
    RFPowerOff(93, "RF Power Off", MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, byteArrayOf(0, 0)),  //

    // SetSuspend(77, "Set Suspend", MinimedTargetType.InitCommand, MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, getByteArray(1)), //
    // CancelSuspend(77, "Cancel Suspend", MinimedTargetType.InitCommand, MedtronicDeviceType.All,MinimedCommandParameterType.FixedParameters, getByteArray(0)), //
    PumpState(131, "Pump State", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters),  //
    ReadPumpErrorStatus(117, "Pump Error Status", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters),  //

    // 511 (InitCommand = 2, Config 7, Data = 1(+3)
    //    DetectBolus(75, "Detect Bolus", MedtronicDeviceType.Medtronic_511, MinimedCommandParameterType.FixedParameters, getByteArray(
    //        0, 0, 0)), //
    // RemoteControlIds(118, "Remote Control Ids", MinimedTargetType.PumpConfiguration_NA, MedtronicDeviceType.All,MinimedCommandParameterType.NoParameters), //
    // FirmwareVersion(116, "Firmware Version", MinimedTargetType.InitCommand, MedtronicDeviceType.All,MinimedCommandParameterType.NoParameters), //
    // PumpId(113, "Pump Id", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.All,MinimedCommandParameterType.NoParameters), // init
    SetRealTimeClock(0x40, "Set Pump Time", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters,  //
        0, R.string.medtronic_cmd_desc_set_time),  //
    GetRealTimeClock(112, "Get Pump Time", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters,  //
        7, R.string.medtronic_cmd_desc_get_time),  // 0x70
    GetBatteryStatus(0x72, "Get Battery Status", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters,
         0, R.string.medtronic_cmd_desc_get_battery_status), //
    GetRemainingInsulin(0x73, "Read Remaining Insulin", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters,
        2, R.string.medtronic_cmd_desc_get_remaining_insulin),  // 115
    SetBolus(0x42, "Set Bolus", MedtronicDeviceType.All, MinimedCommandParameterType.NoParameters,  //
        0, R.string.medtronic_cmd_desc_set_bolus),  // 66

    // 512
    ReadTemporaryBasal(0x98, "Read Temporary Basal", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        5, R.string.medtronic_cmd_desc_get_tbr),  // 152
    SetTemporaryBasal(76, "Set Temporay Basal", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        0, R.string.medtronic_cmd_desc_set_tbr),  // 512 Config
    PumpModel(141, "Pump Model", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        5, R.string.medtronic_cmd_desc_get_model),  // 0x8D

    // BGTargets_512(140, "BG Targets", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512_712,
    // MinimedCommandParameterType.NoParameters), //
    // BGUnits(137, "BG Units", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512andHigher,
    // MinimedCommandParameterType.NoParameters), //
    // Language(134, "Language", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512andHigher,
    // MinimedCommandParameterType.NoParameters), //
    Settings_512(145, "Configuration", MedtronicDeviceType.Medtronic_512_712, MinimedCommandParameterType.NoParameters,  //
        64, 1, 18, R.string.medtronic_cmd_desc_get_settings),  //

    // 512 Data
    GetHistoryData(128, "Get History", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.SubCommands,  //
        1024, 16, 1024, R.string.medtronic_cmd_desc_get_history),  // 0x80
    GetBasalProfileSTD(146, "Get Profile Standard", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 3, 192, R.string.medtronic_cmd_desc_get_basal_profile),  // 146
    GetBasalProfileA(147, "Get Profile A", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 3, 192, R.string.medtronic_cmd_desc_get_basal_profile),
    GetBasalProfileB(148, "Get Profile B", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 3, 192, R.string.medtronic_cmd_desc_get_basal_profile),  // 148
    SetBasalProfileSTD(0x6f, "Set Profile Standard", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 3, 192, R.string.medtronic_cmd_desc_set_basal_profile),  // 111
    SetBasalProfileA(0x30, "Set Profile A", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 3, 192, R.string.medtronic_cmd_desc_set_basal_profile),  // 48
    SetBasalProfileB(0x31, "Set Profile B", MedtronicDeviceType.Medtronic_512andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 3, 192, R.string.medtronic_cmd_desc_set_basal_profile),  // 49

    // 515
    PumpStatus(206, "Pump Status", MedtronicDeviceType.Medtronic_515andHigher, MinimedCommandParameterType.NoParameters),  // PumpConfiguration
    Settings(192, "Configuration", MedtronicDeviceType.Medtronic_515andHigher, MinimedCommandParameterType.NoParameters,  //
        64, 1, 21, R.string.medtronic_cmd_desc_get_settings),  //

    // 522
    SensorSettings_522(153, "Sensor Configuration", MedtronicDeviceType.Medtronic_522andHigher, MinimedCommandParameterType.NoParameters),  //
    GlucoseHistory(154, "Glucose History", MedtronicDeviceType.Medtronic_522andHigher, MinimedCommandParameterType.SubCommands, 1024, 32, 0, null),  //

    // 523
    SensorSettings(207, "Sensor Configuration", MedtronicDeviceType.Medtronic_523andHigher, MinimedCommandParameterType.NoParameters),  //

    // 553
    // 554
    // var MESSAGES = {
    // READ_TIME : 0x70,
    // READ_BATTERY_STATUS: 0x72,
    // READ_HISTORY : 0x80,
    // READ_CARB_RATIOS : 0x8A,
    // READ_INSULIN_SENSITIVITIES: 0x8B,
    // READ_MODEL : 0x8D,
    // READ_PROFILE_STD : 0x92,
    // READ_PROFILE_A : 0x93,
    // READ_PROFILE_B : 0x94,
    // READ_CBG_HISTORY: 0x9A,
    // READ_ISIG_HISTORY: 0x9B,
    // READ_CURRENT_PAGE : 0x9D,
    // READ_BG_TARGETS : 0x9F,
    // READ_SETTINGS : 0xC0, 192
    // READ_CURRENT_CBG_PAGE : 0xCD
    // };
    // Fake Commands
    CancelTBR;



    companion object {
        var mapByCode: MutableMap<Byte, MedtronicCommandType> = HashMap()

        // private fun getDeviceTypesArray(vararg types: MedtronicDeviceType): HashMap<MedtronicDeviceType, String?> {
        //     val hashMap = HashMap<MedtronicDeviceType, String?>()
        //     for (type in types) {
        //         hashMap[type] = null
        //     }
        //     return hashMap
        // }

        fun getByCode(code: Byte): MedtronicCommandType? {
            return if (mapByCode.containsKey(code)) {
                mapByCode[code]
            } else {
                InvalidCommand
            }
        }

        fun constructMessageBody(messageType: MedtronicCommandType?, bodyData: ByteArray?): MessageBody {
            return when (messageType) {
                CommandACK -> PumpAckMessageBody(bodyData)
                else       -> UnknownMessageBody(bodyData!!)
            }
        }

        @JvmStatic
        fun getSettings(medtronicPumpModel: MedtronicDeviceType?): MedtronicCommandType {
            return if (isSameDevice(medtronicPumpModel!!, MedtronicDeviceType.Medtronic_512_712)) Settings_512 else Settings
        }

        init {
            RFPowerOn.maxAllowedTime = 17000
            RFPowerOn.allowedRetries = 0
            RFPowerOn.recordLength = 0
            RFPowerOn.minimalBufferSizeToStartReading = 1
            for (medtronicCommandType in values()) {
                mapByCode[medtronicCommandType.commandCode] = medtronicCommandType
            }
        }
    }

    var commandCode: Byte = 0
    var commandDescription = ""
    var commandParameters: ByteArray? = null
    var commandParametersCount = 0
    var maxRecords = 1
    var resourceId: Int? = null
        private set
    var allowedRetries = 2
    var maxAllowedTime = 2000
    var parameterType: MinimedCommandParameterType? = null
    var minimalBufferSizeToStartReading = 14
    var expectedLength = 0
    var devices: MedtronicDeviceType? = null
    private var recordLength = 64

    constructor() {
        // this is for "fake" commands needed by AAPS MedtronicUITask
    }

    constructor(code: Int, description: String, devices: MedtronicDeviceType?,
                         parameterType: MinimedCommandParameterType?, cmd_params: ByteArray) : this(code, description, devices, parameterType) {
        commandParameters = cmd_params
        commandParametersCount = cmd_params.size
    }

    // NEW
    constructor(code: Int, description: String, devices: MedtronicDeviceType?,  //
                         parameterType: MinimedCommandParameterType?, expectedLength: Int) : this(code, description, devices, parameterType, 64, 1, expectedLength, null) {
    }

    // NEW
    constructor(code: Int, description: String, devices: MedtronicDeviceType?,  //
                         parameterType: MinimedCommandParameterType?, expectedLength: Int, resourceId: Int) : this(code, description, devices, parameterType, 64, 1, expectedLength, resourceId) {
    }

    // NEW
    constructor(code: Int,
                description: String,
                devices: MedtronicDeviceType?,  //
                parameterType: MinimedCommandParameterType?,
                recordLength: Int = 64,
                max_recs: Int = 1,
                expectedLength: Int = 0,
                resourceId: Int? = null) {
        commandCode = code.toByte()
        commandDescription = description
        this.devices = devices
        this.recordLength = recordLength
        maxRecords = max_recs
        this.resourceId = resourceId
        commandParametersCount = 0
        allowedRetries = 2
        this.parameterType = parameterType
        this.expectedLength = expectedLength
        if (this.parameterType == MinimedCommandParameterType.SubCommands) {
            minimalBufferSizeToStartReading = 200
        }
    }

    // @Deprecated("")
    // constructor(code: Int, description: String, devices: MedtronicDeviceType?,  //
    //                      parameterType: MinimedCommandParameterType?, recordLength: Int, max_recs: Int, addy: Int,  //
    //                      addy_len: Int, cmd_type: Int, expectedLength: Int) {
    //     commandCode = code.toByte()
    //     commandDescription = description
    //     //this.targetType = targetType;
    //     this.devices = devices
    //     this.recordLength = recordLength
    //     maxRecords = max_recs
    //     command_type = cmd_type
    //     commandParametersCount = 0
    //     allowedRetries = 2
    //     this.parameterType = parameterType
    //     this.expectedLength = expectedLength
    //     if (this.parameterType == MinimedCommandParameterType.SubCommands) {
    //         minimalBufferSizeToStartReading = 200
    //     }
    // }


    override fun toString(): String {
        return name
    }

    enum class MinimedCommandParameterType {
        NoParameters,  //
        FixedParameters,  //
        SubCommands //
    }
}