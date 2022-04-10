package info.nightscout.androidaps.plugins.pump.insight.app_layer.history;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.HistoryReadingDirectionIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class StartReadingHistoryMessage extends AppLayerMessage {

    private long offset;
    private HistoryReadingDirection direction;

    public StartReadingHistoryMessage() {
        super(MessagePriority.NORMAL, false, true, Service.HISTORY);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(8);
        byteBuf.putUInt16LE(31);
        byteBuf.putUInt16LE(HistoryReadingDirectionIDs.IDS.getID(direction));
        byteBuf.putUInt32LE(offset);
        return byteBuf;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public void setDirection(HistoryReadingDirection direction) {
        this.direction = direction;
    }
}
