package info.nightscout.androidaps.plugins.pump.insight.app_layer.connection;

import org.spongycastle.util.encoders.Hex;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class BindMessage extends AppLayerMessage {

    public BindMessage() {
        super(MessagePriority.NORMAL, false, false, Service.CONNECTION);
    }

    @Override
    protected ByteBuf getData() {
        return ByteBuf.from(Hex.decode("3438310000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
    }
}
