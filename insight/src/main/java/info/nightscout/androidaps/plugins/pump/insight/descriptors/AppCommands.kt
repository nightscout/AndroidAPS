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
        fun fromId(id: Int) : Class<out AppLayerMessage?>? = when (id) {
            CONNECTMESSAGE                        -> ConnectMessage::class.java
            BINDMESSAGE                           -> BindMessage::class.java
            DISCONNECTMESSAGE                     -> DisconnectMessage::class.java
            ACTIVATESERVICEMESSAGE                -> ActivateServiceMessage::class.java
            SERVICECHALLENGEMESSAGE               -> ServiceChallengeMessage::class.java
            GETACTIVEALERTMESSAGE                 -> GetActiveAlertMessage::class.java
            GETACTIVEBOLUSESMESSAGE               -> GetActiveBolusesMessage::class.java
            GETACTIVETBRMESSAGE                   -> GetActiveTBRMessage::class.java
            GETAVAILABLEBOLUSTYPESMESSAGE         -> GetAvailableBolusTypesMessage::class.java
            GETBATTERYSTATUSMESSAGE               -> GetBatteryStatusMessage::class.java
            GETCARTRIDGESTATUSMESSAGE             -> GetCartridgeStatusMessage::class.java
            GETDATETIMEMESSAGE                    -> GetDateTimeMessage::class.java
            GETFIRMWAREVERSIONSMESSAGE            -> GetFirmwareVersionsMessage::class.java
            GETOPERATINGMODEMESSAGE               -> GetOperatingModeMessage::class.java
            GETPUMPSTATUSREGISTERMESSAGE          -> GetPumpStatusRegisterMessage::class.java
            RESETPUMPSTATUSREGISTERMESSAGE        -> ResetPumpStatusRegisterMessage::class.java
            GETACTIVEBASALRATEMESSAGE             -> GetActiveBasalRateMessage::class.java
            GETTOTALDAILYDOSEMESSAGE              -> GetTotalDailyDoseMessage::class.java
            CANCELTBRMESSAGE                      -> CancelTBRMessage::class.java
            CANCELBOLUSMESSAGE                    -> CancelBolusMessage::class.java
            SETOPERATINGMODEMESSAGE               -> SetOperatingModeMessage::class.java
            READPARAMETERBLOCKMESSAGE             -> ReadParameterBlockMessage::class.java
            WRITECONFIGURATIONBLOCKMESSAGE        -> WriteConfigurationBlockMessage::class.java
            CLOSECONFIGURATIONWRITESESSIONMESSAGE -> CloseConfigurationWriteSessionMessage::class.java
            OPENCONFIGURATIONWRITESESSIONMESSAGE  -> OpenConfigurationWriteSessionMessage::class.java
            DELIVERBOLUSMESSAGE                   -> DeliverBolusMessage::class.java
            SETTBRMESSAGE                         -> SetTBRMessage::class.java
            CHANGETBRMESSAGE                      -> ChangeTBRMessage::class.java
            READHISTORYEVENTSMESSAGE              -> ReadHistoryEventsMessage::class.java
            STARTREADINGHISTORYMESSAGE            -> StartReadingHistoryMessage::class.java
            STOPREADINGHISTORYMESSAGE             -> StopReadingHistoryMessage::class.java
            CONFIRMALERTMESSAGE                   -> ConfirmAlertMessage::class.java
            SNOOZEALERTMESSAGE                    -> SnoozeAlertMessage::class.java
            SETDATETIMEMESSAGE                    -> SetDateTimeMessage::class.java
            else                                  -> null                               // if wrong id received return null
        }

        fun fromType(type: Class<out AppLayerMessage?>) : Int = when(type) {
            ConnectMessage::class.java                        -> CONNECTMESSAGE
            BindMessage::class.java                           -> BINDMESSAGE
            DisconnectMessage::class.java                     -> DISCONNECTMESSAGE
            ActivateServiceMessage::class.java                -> ACTIVATESERVICEMESSAGE
            ServiceChallengeMessage::class.java               -> SERVICECHALLENGEMESSAGE
            GetActiveAlertMessage::class.java                 -> GETACTIVEALERTMESSAGE
            GetActiveBolusesMessage::class.java               -> GETACTIVEBOLUSESMESSAGE
            GetActiveTBRMessage::class.java                   -> GETACTIVETBRMESSAGE
            GetAvailableBolusTypesMessage::class.java         -> GETAVAILABLEBOLUSTYPESMESSAGE
            GetBatteryStatusMessage::class.java               -> GETBATTERYSTATUSMESSAGE
            GetCartridgeStatusMessage::class.java             -> GETCARTRIDGESTATUSMESSAGE
            GetDateTimeMessage::class.java                    -> GETDATETIMEMESSAGE
            GetFirmwareVersionsMessage::class.java            -> GETFIRMWAREVERSIONSMESSAGE
            GetOperatingModeMessage::class.java               -> GETOPERATINGMODEMESSAGE
            GetPumpStatusRegisterMessage::class.java          -> GETPUMPSTATUSREGISTERMESSAGE
            ResetPumpStatusRegisterMessage::class.java        -> RESETPUMPSTATUSREGISTERMESSAGE
            GetActiveBasalRateMessage::class.java             -> GETACTIVEBASALRATEMESSAGE
            GetTotalDailyDoseMessage::class.java              -> GETTOTALDAILYDOSEMESSAGE
            CancelTBRMessage::class.java                      -> CANCELTBRMESSAGE
            CancelBolusMessage::class.java                    -> CANCELBOLUSMESSAGE
            SetOperatingModeMessage::class.java               -> SETOPERATINGMODEMESSAGE
            ReadParameterBlockMessage::class.java             -> READPARAMETERBLOCKMESSAGE
            WriteConfigurationBlockMessage::class.java        -> WRITECONFIGURATIONBLOCKMESSAGE
            CloseConfigurationWriteSessionMessage::class.java -> CLOSECONFIGURATIONWRITESESSIONMESSAGE
            OpenConfigurationWriteSessionMessage::class.java  -> OPENCONFIGURATIONWRITESESSIONMESSAGE
            DeliverBolusMessage::class.java                   -> DELIVERBOLUSMESSAGE
            SetTBRMessage::class.java                         -> SETTBRMESSAGE
            ChangeTBRMessage::class.java                      -> CHANGETBRMESSAGE
            ReadHistoryEventsMessage::class.java              -> READHISTORYEVENTSMESSAGE
            StartReadingHistoryMessage::class.java            -> STARTREADINGHISTORYMESSAGE
            StopReadingHistoryMessage::class.java             -> STOPREADINGHISTORYMESSAGE
            ConfirmAlertMessage::class.java                   -> CONFIRMALERTMESSAGE
            SnoozeAlertMessage::class.java                    -> SNOOZEALERTMESSAGE
            SetDateTimeMessage::class.java                    -> SETDATETIMEMESSAGE
            else                                              -> 0                      //I think when is exhaustive but kotlin request an else branch here
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