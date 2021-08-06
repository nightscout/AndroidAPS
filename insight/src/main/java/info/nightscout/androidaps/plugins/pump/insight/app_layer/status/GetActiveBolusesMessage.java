package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.ActiveBolusTypeIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class GetActiveBolusesMessage extends AppLayerMessage {

    private List<ActiveBolus> activeBoluses;

    public GetActiveBolusesMessage() {
        super(MessagePriority.NORMAL, true, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        activeBoluses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ActiveBolus activeBolus = new ActiveBolus();
            activeBolus.setBolusID(byteBuf.readUInt16LE());
            activeBolus.setBolusType(ActiveBolusTypeIDs.IDS.getType(byteBuf.readUInt16LE()));
            byteBuf.shift(2);
            byteBuf.shift(2);
            activeBolus.setInitialAmount(byteBuf.readUInt16Decimal());
            activeBolus.setRemainingAmount(byteBuf.readUInt16Decimal());
            activeBolus.setRemainingDuration(byteBuf.readUInt16LE());
            if (activeBolus.getBolusType() != null) activeBoluses.add(activeBolus);
        }
    }

    public List<ActiveBolus> getActiveBoluses() {
        return this.activeBoluses;
    }
}
