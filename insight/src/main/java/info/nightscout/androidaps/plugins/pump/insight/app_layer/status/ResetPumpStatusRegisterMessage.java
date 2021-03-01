package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ResetPumpStatusRegisterMessage extends AppLayerMessage {

    private boolean operatingModeChanged;
    private boolean batteryStatusChanged;
    private boolean cartridgeStatusChanged;
    private boolean totalDailyDoseChanged;
    private boolean activeBasalRateChanged;
    private boolean activeTBRChanged;
    private boolean activeBolusesChanged;

    public ResetPumpStatusRegisterMessage() {
        super(MessagePriority.NORMAL, false, false, Service.STATUS);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(28);
        byteBuf.putBoolean(operatingModeChanged);
        byteBuf.putBoolean(batteryStatusChanged);
        byteBuf.putBoolean(cartridgeStatusChanged);
        byteBuf.putBoolean(totalDailyDoseChanged);
        byteBuf.putBoolean(activeBasalRateChanged);
        byteBuf.putBoolean(activeTBRChanged);
        byteBuf.putBoolean(activeBolusesChanged);
        for (int i = 0; i < 7; i++) byteBuf.putBoolean(false);
        return byteBuf;
    }

    public void setOperatingModeChanged(boolean operatingModeChanged) {
        this.operatingModeChanged = operatingModeChanged;
    }

    public void setBatteryStatusChanged(boolean batteryStatusChanged) {
        this.batteryStatusChanged = batteryStatusChanged;
    }

    public void setCartridgeStatusChanged(boolean cartridgeStatusChanged) {
        this.cartridgeStatusChanged = cartridgeStatusChanged;
    }

    public void setTotalDailyDoseChanged(boolean totalDailyDoseChanged) {
        this.totalDailyDoseChanged = totalDailyDoseChanged;
    }

    public void setActiveBasalRateChanged(boolean activeBasalRateChanged) {
        this.activeBasalRateChanged = activeBasalRateChanged;
    }

    public void setActiveTBRChanged(boolean activeTBRChanged) {
        this.activeTBRChanged = activeTBRChanged;
    }

    public void setActiveBolusesChanged(boolean activeBolusesChanged) {
        this.activeBolusesChanged = activeBolusesChanged;
    }
}
