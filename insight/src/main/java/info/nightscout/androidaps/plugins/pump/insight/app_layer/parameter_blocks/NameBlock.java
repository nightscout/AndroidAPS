package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public abstract class NameBlock extends ParameterBlock {

    private String name;

    @Override
    public void parse(ByteBuf byteBuf) {
        name = byteBuf.readUTF16(40);
    }

    @Override
    public ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(42);
        byteBuf.putUTF16(name, 40);
        return byteBuf;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
