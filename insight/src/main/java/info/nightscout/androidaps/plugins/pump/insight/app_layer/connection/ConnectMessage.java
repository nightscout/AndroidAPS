package info.nightscout.androidaps.plugins.pump.insight.app_layer.connection;

import org.spongycastle.util.encoders.Hex;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ConnectMessage extends AppLayerMessage {

    public ConnectMessage() {
        super(MessagePriority.NORMAL, false, false, Service.CONNECTION);
    }

    @Override
    protected ByteBuf getData() {
        return ByteBuf.from(Hex.decode("0000080100196000"));
    }
}
