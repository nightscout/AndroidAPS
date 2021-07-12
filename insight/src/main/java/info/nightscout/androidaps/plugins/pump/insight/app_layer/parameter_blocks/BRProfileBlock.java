package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BasalProfileBlock;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public abstract class BRProfileBlock extends ParameterBlock {

    private List<BasalProfileBlock> profileBlocks;

    @Override
    public void parse(ByteBuf byteBuf) {
        profileBlocks = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            BasalProfileBlock basalProfileBlock = new BasalProfileBlock();
            basalProfileBlock.setDuration(byteBuf.readUInt16LE());
            profileBlocks.add(basalProfileBlock);
        }
        for (int i = 0; i < 24; i++) profileBlocks.get(i).setBasalAmount(byteBuf.readUInt16Decimal());
        Iterator<BasalProfileBlock> iterator = profileBlocks.iterator();
        while (iterator.hasNext())
            if (iterator.next().getDuration() == 0)
                iterator.remove();
    }

    @Override
    public ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(96);
        for (int i = 0; i < 24; i++) {
            if (profileBlocks.size() > i) byteBuf.putUInt16LE(profileBlocks.get(i).getDuration());
            else byteBuf.putUInt16LE(0);
        }
        for (int i = 0; i < 24; i++) {
            if (profileBlocks.size() > i) byteBuf.putUInt16Decimal(profileBlocks.get(i).getBasalAmount());
            else byteBuf.putUInt16Decimal(0);
        }
        return byteBuf;
    }

    public List<BasalProfileBlock> getProfileBlocks() {
        return profileBlocks;
    }

    public void setProfileBlocks(List<BasalProfileBlock> profileBlocks) {
        this.profileBlocks = profileBlocks;
    }
}
