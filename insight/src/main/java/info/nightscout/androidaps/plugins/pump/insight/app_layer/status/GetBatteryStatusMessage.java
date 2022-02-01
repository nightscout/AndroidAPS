package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BatteryStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.BatteryTypeIDs;
import info.nightscout.androidaps.plugins.pump.insight.ids.SymbolStatusIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

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
