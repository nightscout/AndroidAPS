package info.nightscout.androidaps.plugins.pump.insight.app_layer;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ParameterBlock;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.ParameterBlockIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ReadParameterBlockMessage extends AppLayerMessage {

    private Class<? extends ParameterBlock> parameterBlockId;
    private ParameterBlock parameterBlock;
    private Service service;

    public ReadParameterBlockMessage() {
        super(MessagePriority.NORMAL, true, false, null);
    }

    @Override
    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(ParameterBlockIDs.IDS.getID(parameterBlockId));
        return byteBuf;
    }

    @Override
    protected void parse(ByteBuf byteBuf) throws Exception {
        parameterBlock = ParameterBlockIDs.IDS.getType(byteBuf.readUInt16LE()).newInstance();
        byteBuf.shift(2); //Restriction level
        parameterBlock.parse(byteBuf);
    }

    public ParameterBlock getParameterBlock() {
        return this.parameterBlock;
    }

    public void setParameterBlockId(Class<? extends ParameterBlock> configurationBlockId) {
        this.parameterBlockId = configurationBlockId;
    }
}
