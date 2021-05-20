package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.ReadParameterBlockMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.*

enum class AppCommands (val id: Int, val type: Class<out AppLayerMessage?>)  {
    CONNECTMESSAGE (61451, ConnectMessage::class.java),
    BINDMESSAGE (62413, BindMessage::class.java),
    DISCONNECTMESSAGE (61460, DisconnectMessage::class.java),
    ACTIVATESERVICEMESSAGE (61687, ActivateServiceMessage::class.java),
    SERVICECHALLENGEMESSAGE (62418, ServiceChallengeMessage::class.java),
    GETACTIVEALERTMESSAGE (985, GetActiveAlertMessage::class.java),
    GETACTIVEBOLUSESMESSAGE (1647, GetActiveBolusesMessage::class.java),
    GETACTIVETBRMESSAGE (1462, GetActiveTBRMessage::class.java),
    GETAVAILABLEBOLUSTYPESMESSAGE (6362, GetAvailableBolusTypesMessage::class.java),
    GETBATTERYSTATUSMESSAGE (805, GetBatteryStatusMessage::class.java),
    GETCARTRIDGESTATUSMESSAGE (826, GetCartridgeStatusMessage::class.java),
    GETDATETIMEMESSAGE (227, GetDateTimeMessage::class.java),
    GETFIRMWAREVERSIONSMESSAGE (11992, GetFirmwareVersionsMessage::class.java),
    GETOPERATINGMODEMESSAGE (252, GetOperatingModeMessage::class.java),
    GETPUMPSTATUSREGISTERMESSAGE (31, GetPumpStatusRegisterMessage::class.java),
    RESETPUMPSTATUSREGISTERMESSAGE (35476, ResetPumpStatusRegisterMessage::class.java),
    GETACTIVEBASALRATEMESSAGE (1449, GetActiveBasalRateMessage::class.java),
    GETTOTALDAILYDOSEMESSAGE (966, GetTotalDailyDoseMessage::class.java),
    CANCELTBRMESSAGE (6201, CancelTBRMessage::class.java),
    CANCELBOLUSMESSAGE (7136, CancelBolusMessage::class.java),
    SETOPERATINGMODEMESSAGE (6182, SetOperatingModeMessage::class.java),
    READPARAMETERBLOCKMESSAGE (7766, ReadParameterBlockMessage::class.java),
    WRITECONFIGURATIONBLOCKMESSAGE (7850, WriteConfigurationBlockMessage::class.java),
    CLOSECONFIGURATIONWRITESESSIONMESSAGE (7861, CloseConfigurationWriteSessionMessage::class.java),
    OPENCONFIGURATIONWRITESESSIONMESSAGE (7753, OpenConfigurationWriteSessionMessage::class.java),
    DELIVERBOLUSMESSAGE (6915, DeliverBolusMessage::class.java),
    SETTBRMESSAGE (6341, SetTBRMessage::class.java),
    CHANGETBRMESSAGE (42067, ChangeTBRMessage::class.java),
    READHISTORYEVENTSMESSAGE (10408, ReadHistoryEventsMessage::class.java),
    STARTREADINGHISTORYMESSAGE (10324, StartReadingHistoryMessage::class.java),
    STOPREADINGHISTORYMESSAGE (38887, StopReadingHistoryMessage::class.java),
    CONFIRMALERTMESSAGE (1683, ConfirmAlertMessage::class.java),
    SNOOZEALERTMESSAGE (1676, SnoozeAlertMessage::class.java),
    SETDATETIMEMESSAGE (7167, SetDateTimeMessage::class.java);

    companion object {
        fun fromType(type: Class<out AppLayerMessage?>) = values().firstOrNull { it.type == type }
        fun fromId(id: Int) = values().firstOrNull { it.id == id }

    }
}