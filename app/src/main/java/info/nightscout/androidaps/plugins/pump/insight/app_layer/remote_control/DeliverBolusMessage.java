package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.BolusTypeIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class DeliverBolusMessage extends AppLayerMessage {

    private BolusType bolusType;
    private double immediateAmount;
    private double extendedAmount;
    private int duration;
    private int bolusId;

    public DeliverBolusMessage() {
        super(MessagePriority.NORMAL, true, true, Service.REMOTE_CONTROL);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(22);
        byteBuf.putUInt16LE(252);
        byteBuf.putUInt16LE(BolusTypeIDs.IDS.getID(bolusType));
        byteBuf.putUInt16LE(31);
        byteBuf.putUInt16LE(0);
        byteBuf.putUInt16Decimal(immediateAmount);
        byteBuf.putUInt16Decimal(extendedAmount);
        byteBuf.putUInt16LE(duration);
        byteBuf.putUInt16LE(0);
        byteBuf.putUInt16Decimal(immediateAmount);
        byteBuf.putUInt16Decimal(extendedAmount);
        byteBuf.putUInt16LE(duration);
        return byteBuf;
    }

    @Override
    protected void parse(ByteBuf byteBuf) throws Exception {
        bolusId = byteBuf.readUInt16LE();
    }

    public void setBolusType(BolusType bolusType) {
        this.bolusType = bolusType;
    }

    public void setImmediateAmount(double immediateAmount) {
        this.immediateAmount = immediateAmount;
    }

    public void setExtendedAmount(double extendedAmount) {
        this.extendedAmount = extendedAmount;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getBolusId() {
        return bolusId;
    }
}
