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
enum class MedtronicCommandType(
    code: Int,
    description: String,
    var devices: MedtronicDeviceType = MedtronicDeviceType.All,
    var parameterType: MinimedCommandParameterType = MinimedCommandParameterType.NoParameters,
    var recordLength: Int = 64,
    var maxRecords: Int = 1,
    var expectedLength: Int = 0,
    var resourceId: Int? = null,
    var commandParameters: ByteArray? = null) {

    InvalidCommand(code = 0, description = "Invalid Command"),  //

    // Pump Responses (9)
    CommandACK(code = 0x06, description = "ACK - Acknowledge"),  //
    CommandNAK(code = 0x15, description = "NAK - Not Acknowledged"),  //

    // All (8)
    PushAck(code = 91, description = "Push ACK", parameterType = MinimedCommandParameterType.FixedParameters, commandParameters = byteArrayOf(2)),  //
    PushEsc(code = 91, description = "Push Esc", parameterType = MinimedCommandParameterType.FixedParameters, commandParameters = byteArrayOf(1)),  //
    PushButton(code = 0x5b, description = "Push Button"),  // 91
    RFPowerOn(code = 93, description = "RF Power On", parameterType = MinimedCommandParameterType.FixedParameters, commandParameters = byteArrayOf(1, 10)),  //
    RFPowerOff(code = 93, description = "RF Power Off", parameterType = MinimedCommandParameterType.FixedParameters, commandParameters = byteArrayOf(0, 0)),  //

    // SetSuspend(77, "Set Suspend", MinimedTargetType.InitCommand, MedtronicDeviceType.All, MinimedCommandParameterType.FixedParameters, getByteArray(1)), //
    // CancelSuspend(77, "Cancel Suspend", MinimedTargetType.InitCommand, MedtronicDeviceType.All,MinimedCommandParameterType.FixedParameters, getByteArray(0)), //
    PumpState(code = 131, description = "Pump State"),  //
    ReadPumpErrorStatus(code = 117, description = "Pump Error Status"),  //

    // 511 (InitCommand = 2, Config 7, Data = 1(+3)
    //    DetectBolus(75, "Detect Bolus", MedtronicDeviceType.Medtronic_511, MinimedCommandParameterType.FixedParameters, getByteArray(
    //        0, 0, 0)), //
    // RemoteControlIds(118, "Remote Control Ids", MinimedTargetType.PumpConfiguration_NA, MedtronicDeviceType.All,MinimedCommandParameterType.NoParameters), //
    // FirmwareVersion(116, "Firmware Version", MinimedTargetType.InitCommand, MedtronicDeviceType.All,MinimedCommandParameterType.NoParameters), //
    // PumpId(113, "Pump Id", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.All,MinimedCommandParameterType.NoParameters), // init
    SetRealTimeClock(code = 0x40, description = "Set Pump Time", recordLength = 0, resourceId = R.string.medtronic_cmd_desc_set_time),  //
    GetRealTimeClock(112, description = "Get Pump Time", recordLength = 7, resourceId = R.string.medtronic_cmd_desc_get_time),  // 0x70
    GetBatteryStatus(code = 0x72, description = "Get Battery Status", recordLength = 0, resourceId = R.string.medtronic_cmd_desc_get_battery_status), //
    GetRemainingInsulin(code = 0x73, description = "Read Remaining Insulin",
        recordLength = 2, resourceId = R.string.medtronic_cmd_desc_get_remaining_insulin),  // 115
    SetBolus(code = 0x42, description = "Set Bolus", recordLength = 0, resourceId = R.string.medtronic_cmd_desc_set_bolus),  // 66

    // 512
    ReadTemporaryBasal(code = 0x98, description = "Read Temporary Basal", devices = MedtronicDeviceType.Medtronic_512andHigher,  //
        recordLength = 5, resourceId = R.string.medtronic_cmd_desc_get_tbr),  // 152
    SetTemporaryBasal(code = 76, description = "Set Temporay Basal", devices = MedtronicDeviceType.Medtronic_512andHigher, //
        recordLength = 0, resourceId = R.string.medtronic_cmd_desc_set_tbr),  // 512 Config
    PumpModel(code = 141, description = "Pump Model", devices = MedtronicDeviceType.Medtronic_512andHigher,   //
        recordLength = 5, resourceId = R.string.medtronic_cmd_desc_get_model),  // 0x8D

    // BGTargets_512(140, "BG Targets", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512_712,
    // MinimedCommandParameterType.NoParameters), //
    // BGUnits(137, "BG Units", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512andHigher,
    // MinimedCommandParameterType.NoParameters), //
    // Language(134, "Language", MinimedTargetType.PumpConfiguration, MedtronicDeviceType.Medtronic_512andHigher,
    // MinimedCommandParameterType.NoParameters), //
    Settings_512(code = 145, description = "Configuration", devices = MedtronicDeviceType.Medtronic_512_712,
        expectedLength = 18, resourceId = R.string.medtronic_cmd_desc_get_settings),  //

    // 512 Data
    GetHistoryData(code = 128, description = "Get History", devices = MedtronicDeviceType.Medtronic_512andHigher,
        parameterType = MinimedCommandParameterType.SubCommands, recordLength = 1024, maxRecords = 16,
        expectedLength = 1024, resourceId = R.string.medtronic_cmd_desc_get_history),  // 0x80
    GetBasalProfileSTD(code = 146, description = "Get Profile Standard", devices = MedtronicDeviceType.Medtronic_512andHigher,  //
        maxRecords = 3, expectedLength = 192, resourceId = R.string.medtronic_cmd_desc_get_basal_profile),  // 146
    GetBasalProfileA(code = 147, description = "Get Profile A", devices = MedtronicDeviceType.Medtronic_512andHigher,   //
        maxRecords = 3, expectedLength = 192, resourceId = R.string.medtronic_cmd_desc_get_basal_profile),
    GetBasalProfileB(code = 148, description = "Get Profile B", devices = MedtronicDeviceType.Medtronic_512andHigher,   //
        maxRecords = 3, expectedLength = 192, resourceId = R.string.medtronic_cmd_desc_get_basal_profile),  // 148
    SetBasalProfileSTD(code = 0x6f, description = "Set Profile Standard", devices = MedtronicDeviceType.Medtronic_512andHigher,   //
        maxRecords = 3, expectedLength = 192, resourceId = R.string.medtronic_cmd_desc_set_basal_profile),  // 111
    SetBasalProfileA(code = 0x30, description = "Set Profile A", devices = MedtronicDeviceType.Medtronic_512andHigher,  //
        maxRecords = 3, expectedLength = 192, resourceId = R.string.medtronic_cmd_desc_set_basal_profile),  // 48
    SetBasalProfileB(code = 0x31, description = "Set Profile B", devices = MedtronicDeviceType.Medtronic_512andHigher,  //
        maxRecords = 3, expectedLength = 192, resourceId = R.string.medtronic_cmd_desc_set_basal_profile),  // 49

    // 515
    PumpStatus(code = 206, description = "Pump Status", devices = MedtronicDeviceType.Medtronic_515andHigher),  // PumpConfiguration
    Settings(code = 192, description = "Configuration", devices = MedtronicDeviceType.Medtronic_515andHigher,
        maxRecords = 1, expectedLength = 21, resourceId = R.string.medtronic_cmd_desc_get_settings),  //

    // 522
    SensorSettings_522(code = 153, description = "Sensor Configuration", devices = MedtronicDeviceType.Medtronic_522andHigher),  //
    GlucoseHistory(code = 154, description = "Glucose History", devices = MedtronicDeviceType.Medtronic_522andHigher,
        MinimedCommandParameterType.SubCommands, recordLength = 1024, maxRecords = 32, expectedLength = 0),  //

    // 523
    SensorSettings(code = 207, description = "Sensor Configuration", devices = MedtronicDeviceType.Medtronic_523andHigher),  //

    // 553
    // 554
    // var MESSAGES = {
    // READ_CARB_RATIOS : 0x8A,
    // READ_INSULIN_SENSITIVITIES: 0x8B,
    // READ_CBG_HISTORY: 0x9A,
    // READ_ISIG_HISTORY: 0x9B,
    // READ_CURRENT_PAGE : 0x9D,
    // READ_BG_TARGETS : 0x9F,
    // READ_SETTINGS : 0xC0, 192
    // READ_CURRENT_CBG_PAGE : 0xCD
    // };
    // Fake Commands
    CancelTBR(code = 250, description = "Cancel TBR", resourceId = R.string.medtronic_cmd_desc_cancel_tbr);

    companion object {

        var mapByCode: MutableMap<Byte, MedtronicCommandType> = HashMap()

        fun getByCode(code: Byte): MedtronicCommandType? {
            return if (mapByCode.containsKey(code)) {
                mapByCode[code]
            } else {
                InvalidCommand
            }
        }

        fun constructMessageBody(messageType: MedtronicCommandType?, bodyData: ByteArray): MessageBody {
            return when (messageType) {
                CommandACK -> PumpAckMessageBody(bodyData)
                else       -> UnknownMessageBody(bodyData)
            }
        }

        fun getSettings(medtronicPumpModel: MedtronicDeviceType): MedtronicCommandType {
            return if (isSameDevice(medtronicPumpModel, MedtronicDeviceType.Medtronic_512_712))
                Settings_512
            else
                Settings
        }

        init {
            for (medtronicCommandType in values()) {
                mapByCode[medtronicCommandType.commandCode] = medtronicCommandType
            }
        }
    }

    var commandCode: Byte = 0
    var commandDescription = description
    var commandParametersCount = 0
    var allowedRetries = 2

    //var maxAllowedTime = 2000
    var minimalBufferSizeToStartReading = 14

    init {
        commandCode = code.toByte()
        this.commandParametersCount = commandParameters?.size ?: 0
        allowedRetries = 2
        if (this.parameterType == MinimedCommandParameterType.SubCommands) {
            minimalBufferSizeToStartReading = 200
        }
    }

    override fun toString(): String {
        return name
    }

    enum class MinimedCommandParameterType {
        NoParameters,  //
        FixedParameters,  //
        SubCommands //
    }
}