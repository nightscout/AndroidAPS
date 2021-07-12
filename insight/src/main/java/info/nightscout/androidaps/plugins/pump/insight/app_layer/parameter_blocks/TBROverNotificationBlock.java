package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class TBROverNotificationBlock extends ParameterBlock {

    private boolean enabled;
    private int melody;

    @Override
    public void parse(ByteBuf byteBuf) {
        enabled = byteBuf.readBoolean();
        melody = byteBuf.readUInt16LE();
    }

    @Override
    public ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(4);
        byteBuf.putBoolean(enabled);
        byteBuf.putUInt16LE(melody);
        return byteBuf;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
