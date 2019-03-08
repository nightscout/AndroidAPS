package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AvailableBolusTypes;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class GetAvailableBolusTypesMessage extends AppLayerMessage {

    private AvailableBolusTypes availableBolusTypes;

    public GetAvailableBolusTypesMessage() {
        super(MessagePriority.NORMAL, false, false, Service.REMOTE_CONTROL);
    }

    @Override
    protected void parse(ByteBuf byteBuf) throws Exception {
        availableBolusTypes = new AvailableBolusTypes();
        availableBolusTypes.setStandardAvailable(byteBuf.readBoolean());
        availableBolusTypes.setExtendedAvailable(byteBuf.readBoolean());
        availableBolusTypes.setMultiwaveAvailable(byteBuf.readBoolean());
    }

    public AvailableBolusTypes getAvailableBolusTypes() {
        return this.availableBolusTypes;
    }
}
