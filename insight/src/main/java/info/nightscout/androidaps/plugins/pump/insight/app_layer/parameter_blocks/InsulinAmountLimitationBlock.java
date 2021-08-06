package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public abstract class InsulinAmountLimitationBlock extends ParameterBlock {

    private double amountLimitation;

    @Override
    public void parse(ByteBuf byteBuf) {
        amountLimitation = byteBuf.readUInt16Decimal();
    }

    @Override
    public ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16Decimal(amountLimitation);
        return byteBuf;
    }

    public double getAmountLimitation() {
        return this.amountLimitation;
    }

    public void setAmountLimitation(double amountLimitation) {
        this.amountLimitation = amountLimitation;
    }
}
