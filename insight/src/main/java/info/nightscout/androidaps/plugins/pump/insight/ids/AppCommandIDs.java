package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SetDateTimeMessage;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.ReadParameterBlockMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ActivateServiceMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.BindMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ConnectMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.DisconnectMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ServiceChallengeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.ReadHistoryEventsMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.StartReadingHistoryMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.StopReadingHistoryMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.CancelBolusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.CancelTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.ChangeTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.ConfirmAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.DeliverBolusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.GetAvailableBolusTypesMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SetOperatingModeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SetTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SnoozeAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveBasalRateMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveBolusesMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetBatteryStatusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetCartridgeStatusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetDateTimeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetFirmwareVersionsMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetOperatingModeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetPumpStatusRegisterMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetTotalDailyDoseMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.ResetPumpStatusRegisterMessage;

public class AppCommandIDs {

    public static final IDStorage<Class<? extends AppLayerMessage>, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(ConnectMessage.class, 61451);
        IDS.put(BindMessage.class, 62413);
        IDS.put(DisconnectMessage.class, 61460);
        IDS.put(ActivateServiceMessage.class, 61687);
        IDS.put(ServiceChallengeMessage.class, 62418);
        IDS.put(GetActiveAlertMessage.class, 985);
        IDS.put(GetActiveBolusesMessage.class, 1647);
        IDS.put(GetActiveTBRMessage.class, 1462);
        IDS.put(GetAvailableBolusTypesMessage.class, 6362);
        IDS.put(GetBatteryStatusMessage.class, 805);
        IDS.put(GetCartridgeStatusMessage.class, 826);
        IDS.put(GetDateTimeMessage.class, 227);
        IDS.put(GetFirmwareVersionsMessage.class, 11992);
        IDS.put(GetOperatingModeMessage.class, 252);
        IDS.put(GetPumpStatusRegisterMessage.class, 31);
        IDS.put(ResetPumpStatusRegisterMessage.class, 35476);
        IDS.put(GetActiveBasalRateMessage.class, 1449);
        IDS.put(GetTotalDailyDoseMessage.class, 966);
        IDS.put(CancelTBRMessage.class, 6201);
        IDS.put(CancelBolusMessage.class, 7136);
        IDS.put(SetOperatingModeMessage.class, 6182);
        IDS.put(ReadParameterBlockMessage.class, 7766);
        IDS.put(WriteConfigurationBlockMessage.class, 7850);
        IDS.put(CloseConfigurationWriteSessionMessage.class, 7861);
        IDS.put(OpenConfigurationWriteSessionMessage.class, 7753);
        IDS.put(DeliverBolusMessage.class, 6915);
        IDS.put(SetTBRMessage.class, 6341);
        IDS.put(ChangeTBRMessage.class, 42067);
        IDS.put(ReadHistoryEventsMessage.class, 10408);
        IDS.put(StartReadingHistoryMessage.class, 10324);
        IDS.put(StopReadingHistoryMessage.class, 38887);
        IDS.put(ConfirmAlertMessage.class, 1683);
        IDS.put(SnoozeAlertMessage.class, 1676);
        IDS.put(SetDateTimeMessage.class, 7167);
    }

}
