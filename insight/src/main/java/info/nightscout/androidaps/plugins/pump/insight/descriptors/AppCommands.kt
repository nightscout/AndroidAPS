package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.ReadParameterBlockMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.*

class AppCommands (val id: Int, val type: Class<out AppLayerMessage?>)  {

    companion object {
        fun fromId(id: Int) : AppLayerMessage? = when (id) {
            CONNECTMESSAGE                        -> ConnectMessage()
            BINDMESSAGE                           -> BindMessage()
            DISCONNECTMESSAGE                     -> DisconnectMessage()
            ACTIVATESERVICEMESSAGE                -> ActivateServiceMessage()
            SERVICECHALLENGEMESSAGE               -> ServiceChallengeMessage()
            GETACTIVEALERTMESSAGE                 -> GetActiveAlertMessage()
            GETACTIVEBOLUSESMESSAGE               -> GetActiveBolusesMessage()
            GETACTIVETBRMESSAGE                   -> GetActiveTBRMessage()
            GETAVAILABLEBOLUSTYPESMESSAGE         -> GetAvailableBolusTypesMessage()
            GETBATTERYSTATUSMESSAGE               -> GetBatteryStatusMessage()
            GETCARTRIDGESTATUSMESSAGE             -> GetCartridgeStatusMessage()
            GETDATETIMEMESSAGE                    -> GetDateTimeMessage()
            GETFIRMWAREVERSIONSMESSAGE            -> GetFirmwareVersionsMessage()
            GETOPERATINGMODEMESSAGE               -> GetOperatingModeMessage()
            GETPUMPSTATUSREGISTERMESSAGE          -> GetPumpStatusRegisterMessage()
            RESETPUMPSTATUSREGISTERMESSAGE        -> ResetPumpStatusRegisterMessage()
            GETACTIVEBASALRATEMESSAGE             -> GetActiveBasalRateMessage()
            GETTOTALDAILYDOSEMESSAGE              -> GetTotalDailyDoseMessage()
            CANCELTBRMESSAGE                      -> CancelTBRMessage()
            CANCELBOLUSMESSAGE                    -> CancelBolusMessage()
            SETOPERATINGMODEMESSAGE               -> SetOperatingModeMessage()
            READPARAMETERBLOCKMESSAGE             -> ReadParameterBlockMessage()
            WRITECONFIGURATIONBLOCKMESSAGE        -> WriteConfigurationBlockMessage()
            CLOSECONFIGURATIONWRITESESSIONMESSAGE -> CloseConfigurationWriteSessionMessage()
            OPENCONFIGURATIONWRITESESSIONMESSAGE  -> OpenConfigurationWriteSessionMessage()
            DELIVERBOLUSMESSAGE                   -> DeliverBolusMessage()
            SETTBRMESSAGE                         -> SetTBRMessage()
            CHANGETBRMESSAGE                      -> ChangeTBRMessage()
            READHISTORYEVENTSMESSAGE              -> ReadHistoryEventsMessage()
            STARTREADINGHISTORYMESSAGE            -> StartReadingHistoryMessage()
            STOPREADINGHISTORYMESSAGE             -> StopReadingHistoryMessage()
            CONFIRMALERTMESSAGE                   -> ConfirmAlertMessage()
            SNOOZEALERTMESSAGE                    -> SnoozeAlertMessage()
            SETDATETIMEMESSAGE                    -> SetDateTimeMessage()
            else                                  -> null                               // if wrong id received return null
        }

        fun fromType(type: AppLayerMessage?) : Int = when(type) {
            is ConnectMessage                        -> CONNECTMESSAGE
            is BindMessage                           -> BINDMESSAGE
            is DisconnectMessage                     -> DISCONNECTMESSAGE
            is ActivateServiceMessage                -> ACTIVATESERVICEMESSAGE
            is ServiceChallengeMessage               -> SERVICECHALLENGEMESSAGE
            is GetActiveAlertMessage                 -> GETACTIVEALERTMESSAGE
            is GetActiveBolusesMessage               -> GETACTIVEBOLUSESMESSAGE
            is GetActiveTBRMessage                   -> GETACTIVETBRMESSAGE
            is GetAvailableBolusTypesMessage         -> GETAVAILABLEBOLUSTYPESMESSAGE
            is GetBatteryStatusMessage               -> GETBATTERYSTATUSMESSAGE
            is GetCartridgeStatusMessage             -> GETCARTRIDGESTATUSMESSAGE
            is GetDateTimeMessage                    -> GETDATETIMEMESSAGE
            is GetFirmwareVersionsMessage            -> GETFIRMWAREVERSIONSMESSAGE
            is GetOperatingModeMessage               -> GETOPERATINGMODEMESSAGE
            is GetPumpStatusRegisterMessage          -> GETPUMPSTATUSREGISTERMESSAGE
            is ResetPumpStatusRegisterMessage        -> RESETPUMPSTATUSREGISTERMESSAGE
            is GetActiveBasalRateMessage             -> GETACTIVEBASALRATEMESSAGE
            is GetTotalDailyDoseMessage              -> GETTOTALDAILYDOSEMESSAGE
            is CancelTBRMessage                      -> CANCELTBRMESSAGE
            is CancelBolusMessage                    -> CANCELBOLUSMESSAGE
            is SetOperatingModeMessage               -> SETOPERATINGMODEMESSAGE
            is ReadParameterBlockMessage             -> READPARAMETERBLOCKMESSAGE
            is WriteConfigurationBlockMessage        -> WRITECONFIGURATIONBLOCKMESSAGE
            is CloseConfigurationWriteSessionMessage -> CLOSECONFIGURATIONWRITESESSIONMESSAGE
            is OpenConfigurationWriteSessionMessage  -> OPENCONFIGURATIONWRITESESSIONMESSAGE
            is DeliverBolusMessage                   -> DELIVERBOLUSMESSAGE
            is SetTBRMessage                         -> SETTBRMESSAGE
            is ChangeTBRMessage                      -> CHANGETBRMESSAGE
            is ReadHistoryEventsMessage              -> READHISTORYEVENTSMESSAGE
            is StartReadingHistoryMessage            -> STARTREADINGHISTORYMESSAGE
            is StopReadingHistoryMessage             -> STOPREADINGHISTORYMESSAGE
            is ConfirmAlertMessage                   -> CONFIRMALERTMESSAGE
            is SnoozeAlertMessage                    -> SNOOZEALERTMESSAGE
            is SetDateTimeMessage                    -> SETDATETIMEMESSAGE
            else                                     -> 0                      //Only if type is null
        }

        const val CONNECTMESSAGE = 61451
        const val BINDMESSAGE = 62413
        const val DISCONNECTMESSAGE = 61460
        const val ACTIVATESERVICEMESSAGE = 61687
        const val SERVICECHALLENGEMESSAGE = 62418
        const val GETACTIVEALERTMESSAGE = 985
        const val GETACTIVEBOLUSESMESSAGE = 1647
        const val GETACTIVETBRMESSAGE = 1462
        const val GETAVAILABLEBOLUSTYPESMESSAGE = 6362
        const val GETBATTERYSTATUSMESSAGE = 805
        const val GETCARTRIDGESTATUSMESSAGE = 826
        const val GETDATETIMEMESSAGE = 227
        const val GETFIRMWAREVERSIONSMESSAGE = 11992
        const val GETOPERATINGMODEMESSAGE = 252
        const val GETPUMPSTATUSREGISTERMESSAGE = 31
        const val RESETPUMPSTATUSREGISTERMESSAGE = 35476
        const val GETACTIVEBASALRATEMESSAGE = 1449
        const val GETTOTALDAILYDOSEMESSAGE = 966
        const val CANCELTBRMESSAGE = 6201
        const val CANCELBOLUSMESSAGE = 7136
        const val SETOPERATINGMODEMESSAGE = 6182
        const val READPARAMETERBLOCKMESSAGE = 7766
        const val WRITECONFIGURATIONBLOCKMESSAGE = 7850
        const val CLOSECONFIGURATIONWRITESESSIONMESSAGE = 7861
        const val OPENCONFIGURATIONWRITESESSIONMESSAGE = 7753
        const val DELIVERBOLUSMESSAGE = 6915
        const val SETTBRMESSAGE = 6341
        const val CHANGETBRMESSAGE = 42067
        const val READHISTORYEVENTSMESSAGE = 10408
        const val STARTREADINGHISTORYMESSAGE = 10324
        const val STOPREADINGHISTORYMESSAGE = 38887
        const val CONFIRMALERTMESSAGE = 1683
        const val SNOOZEALERTMESSAGE = 1676
        const val SETDATETIMEMESSAGE = 7167

    }
}