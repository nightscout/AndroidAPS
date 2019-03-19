package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.status;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.BatteryStatus;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.BatteryTypeIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.SymbolStatusIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class GetBatteryStatusMessage extends AppLayerMessage {

    private BatteryStatus batteryStatus;

    public GetBatteryStatusMessage() {
        super(MessagePriority.NORMAL, false, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        batteryStatus = new BatteryStatus();
        batteryStatus.setBatteryType(BatteryTypeIDs.IDS.getType(byteBuf.readUInt16LE()));
        batteryStatus.setBatteryAmount(byteBuf.readUInt16LE());
        batteryStatus.setSymbolStatus(SymbolStatusIDs.IDS.getType(byteBuf.readUInt16LE()));
    }

    public BatteryStatus getBatteryStatus() {
        return this.batteryStatus;
    }
}
