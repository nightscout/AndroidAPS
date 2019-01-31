package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.remote_control;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class SetTBRMessage extends AppLayerMessage {

    private int percentage;
    private int duration;

    public SetTBRMessage() {
        super(MessagePriority.NORMAL, false, true, Service.REMOTE_CONTROL);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(6);
        byteBuf.putUInt16LE(percentage);
        byteBuf.putUInt16LE(duration);
        byteBuf.putUInt16LE(31);
        return byteBuf;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
