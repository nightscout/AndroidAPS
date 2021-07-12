package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.pump.insight.ids.OperatingModeIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

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
