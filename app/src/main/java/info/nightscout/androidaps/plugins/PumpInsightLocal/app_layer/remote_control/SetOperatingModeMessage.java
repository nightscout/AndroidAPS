package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.remote_control;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.OperatingModeIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class SetOperatingModeMessage extends AppLayerMessage {

    private OperatingMode operatingMode;

    public SetOperatingModeMessage() {
        super(MessagePriority.HIGHEST, false, true, Service.REMOTE_CONTROL);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(OperatingModeIDs.IDS.getID(operatingMode));
        return byteBuf;
    }

    public void setOperatingMode(OperatingMode operatingMode) {
        this.operatingMode = operatingMode;
    }
}
