package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BasalProfile;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ActiveBRProfileBlock extends ParameterBlock {

    private BasalProfile activeBasalProfile;

    @Override
    public void parse(ByteBuf byteBuf) {
        activeBasalProfile = BasalProfile.Companion.fromId(byteBuf.readUInt16LE());
    }

    @Override
    public ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(activeBasalProfile.getId());
        return byteBuf;
    }

    public BasalProfile getActiveBasalProfile() {
        return activeBasalProfile;
    }

    public void setActiveBasalProfile(BasalProfile activeBasalProfile) {
        this.activeBasalProfile = activeBasalProfile;
    }
}
