package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.pump.insight.ids.OperatingModeIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

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
