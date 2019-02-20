package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.status;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.OperatingModeIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class GetOperatingModeMessage extends AppLayerMessage {

    private OperatingMode operatingMode;

    public GetOperatingModeMessage() {
        super(MessagePriority.NORMAL, true, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        this.operatingMode = OperatingModeIDs.IDS.getType(byteBuf.readUInt16LE());
    }

    public OperatingMode getOperatingMode() {
        return this.operatingMode;
    }
}
