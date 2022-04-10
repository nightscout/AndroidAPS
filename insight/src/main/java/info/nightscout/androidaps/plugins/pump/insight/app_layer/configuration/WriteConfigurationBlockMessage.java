package info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ParameterBlock;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.ParameterBlockIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class WriteConfigurationBlockMessage extends AppLayerMessage {

    private ParameterBlock parameterBlock;
    private Class<? extends ParameterBlock> configurationBlockId;

    public WriteConfigurationBlockMessage() {
        super(MessagePriority.NORMAL, false, true, Service.CONFIGURATION);
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf configBlockData = parameterBlock.getData();
        ByteBuf data = new ByteBuf(4 + configBlockData.getSize());
        data.putUInt16LE(ParameterBlockIDs.IDS.getID(parameterBlock.getClass()));
        data.putUInt16LE(31);
        data.putByteBuf(configBlockData);
        return data;
    }

    @Override
    protected void parse(ByteBuf byteBuf) throws Exception {
        configurationBlockId = ParameterBlockIDs.IDS.getType(byteBuf.readUInt16LE());
    }

    public Class<? extends ParameterBlock> getConfigurationBlockId() {
        return this.configurationBlockId;
    }

    public void setParameterBlock(ParameterBlock parameterBlock) {
        this.parameterBlock = parameterBlock;
    }
}
