package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public abstract class ParameterBlock {

    public abstract void parse(ByteBuf byteBuf);
    public abstract ByteBuf getData();

}
