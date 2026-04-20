package info.nightscout.androidaps.plugins.pump.carelevo.presentation.model

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo

sealed class CarelevoOverviewEvent : Event {

    data object NoAction : CarelevoOverviewEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoOverviewEvent()
    data object ShowMessageCarelevoIsNotConnected : CarelevoOverviewEvent()
    data object DiscardComplete : CarelevoOverviewEvent()
    data object DiscardFailed : CarelevoOverviewEvent()
    data object ResumePumpComplete : CarelevoOverviewEvent()
    data object ResumePumpFailed : CarelevoOverviewEvent()
    data object StopPumpComplete : CarelevoOverviewEvent()
    data object StopPumpFailed : CarelevoOverviewEvent()

    data object ClickPumpStopResumeBtn : CarelevoOverviewEvent()
    data object ShowPumpStopDurationSelectDialog : CarelevoOverviewEvent()
    data object ShowPumpResumeDialog : CarelevoOverviewEvent()
}

sealed class CarelevoConnectEvent : Event {

    data object NoAction : CarelevoConnectEvent()
    data object DiscardComplete : CarelevoConnectEvent()
    data object DiscardFailed : CarelevoConnectEvent()

}

sealed class CarelevoConnectPrepareEvent : Event {

    data object NoAction : CarelevoConnectPrepareEvent()
    data object ShowConnectDialog : CarelevoConnectPrepareEvent()
    data object ShowMessageScanFailed : CarelevoConnectPrepareEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoConnectPrepareEvent()
    data object ShowMessageScanIsWorking : CarelevoConnectPrepareEvent()
    data object ShowMessageSelectedDeviceIseEmpty : CarelevoConnectPrepareEvent()
    data object ShowMessageNotSetUserSettingInfo : CarelevoConnectPrepareEvent()

    data object ConnectComplete : CarelevoConnectPrepareEvent()
    data object ConnectFailed : CarelevoConnectPrepareEvent()

    data object DiscardComplete : CarelevoConnectPrepareEvent()
    data object DiscardFailed : CarelevoConnectPrepareEvent()
}

sealed class CarelevoConnectSafetyCheckEvent : Event {

    data object NoAction : CarelevoConnectSafetyCheckEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoConnectSafetyCheckEvent()
    data object ShowMessageCarelevoIsNotConnected : CarelevoConnectSafetyCheckEvent()
    data object SafetyCheckProgress : CarelevoConnectSafetyCheckEvent()
    data object SafetyCheckComplete : CarelevoConnectSafetyCheckEvent()
    data object SafetyCheckFailed : CarelevoConnectSafetyCheckEvent()
    data object DiscardComplete : CarelevoConnectSafetyCheckEvent()
    data object DiscardFailed : CarelevoConnectSafetyCheckEvent()

}

sealed class CarelevoConnectNeedleEvent : Event {

    data object NoAction : CarelevoConnectNeedleEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoConnectNeedleEvent()
    data object ShowMessageCarelevoIsNotConnected : CarelevoConnectNeedleEvent()
    data object ShowMessageProfileNotSet : CarelevoConnectNeedleEvent()
    data class CheckNeedleComplete(val result: Boolean) : CarelevoConnectNeedleEvent()
    data class CheckNeedleFailed(val failedCount: Int) : CarelevoConnectNeedleEvent()
    data object CheckNeedleError : CarelevoConnectNeedleEvent()
    data object DiscardComplete : CarelevoConnectNeedleEvent()
    data object DiscardFailed : CarelevoConnectNeedleEvent()
    data object SetBasalComplete : CarelevoConnectNeedleEvent()
    data object SetBasalFailed : CarelevoConnectNeedleEvent()
}

sealed class CarelevoCommunicationCheckEvent : Event {

    data object NoAction : CarelevoCommunicationCheckEvent()
    data object ShowMessageBluetoothNotEnabled : CarelevoCommunicationCheckEvent()
    data object ShowMessagePatchAddressInvalid : CarelevoCommunicationCheckEvent()
    data object CommunicationCheckComplete : CarelevoCommunicationCheckEvent()
    data object CommunicationCheckFailed : CarelevoCommunicationCheckEvent()
    data object DiscardComplete : CarelevoCommunicationCheckEvent()
    data object DiscardFailed : CarelevoCommunicationCheckEvent()

}

sealed class AlarmEvent : Event {
    data object NoAction : AlarmEvent()
    data class ClearAlarm(val info: CarelevoAlarmInfo) : AlarmEvent()
    data object RequestBluetoothEnable : AlarmEvent()
    data class ShowToastMessage(
        @StringRes val messageRes: Int
    ) : AlarmEvent()

    data object Mute : AlarmEvent()
    data object Mute5min : AlarmEvent()
    data object StartAlarm : AlarmEvent()
}
