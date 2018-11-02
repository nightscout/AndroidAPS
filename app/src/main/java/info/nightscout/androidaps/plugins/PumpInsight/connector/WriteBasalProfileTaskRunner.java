package info.nightscout.androidaps.plugins.PumpInsight.connector;

import java.util.List;

import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ActiveProfileBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile1Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile2Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile3Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile4Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile5Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfileBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ConfigurationBlock;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.configuration.CloseWriteSessionMessage;
import sugar.free.sightparser.applayer.messages.configuration.OpenWriteSessionMessage;
import sugar.free.sightparser.applayer.messages.configuration.ReadConfigurationBlockMessage;
import sugar.free.sightparser.applayer.messages.configuration.WriteConfigurationBlockMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

/**
 * Created by Tebbe Ubben on 10.03.2018.
 */

public class WriteBasalProfileTaskRunner extends TaskRunner {

    private List<BRProfileBlock.ProfileBlock> profileBlocks;
    private BRProfileBlock profileBlock;

    public WriteBasalProfileTaskRunner(SightServiceConnector serviceConnector, List<BRProfileBlock.ProfileBlock> profileBlocks) {
        super(serviceConnector);
        this.profileBlocks = profileBlocks;
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) {
            ReadConfigurationBlockMessage readMessage = new ReadConfigurationBlockMessage();
            readMessage.setConfigurationBlockID(ActiveProfileBlock.ID);
            return readMessage;
        } else if (message instanceof ReadConfigurationBlockMessage) {
            ConfigurationBlock configurationBlock = ((ReadConfigurationBlockMessage) message).getConfigurationBlock();
            ActiveProfileBlock activeProfileBlock = (ActiveProfileBlock) configurationBlock;
            switch (activeProfileBlock.getActiveProfile()) {
                case BR_PROFILE_1:
                    profileBlock = new BRProfile1Block();
                    break;
                case BR_PROFILE_2:
                    profileBlock = new BRProfile2Block();
                    break;
                case BR_PROFILE_3:
                    profileBlock = new BRProfile3Block();
                    break;
                case BR_PROFILE_4:
                    profileBlock = new BRProfile4Block();
                    break;
                case BR_PROFILE_5:
                    profileBlock = new BRProfile5Block();
                    break;
            }
            profileBlock.setProfileBlocks(profileBlocks);
            return new OpenWriteSessionMessage();
        } else if (message instanceof OpenWriteSessionMessage) {
            WriteConfigurationBlockMessage writeMessage = new WriteConfigurationBlockMessage();
            writeMessage.setConfigurationBlock(profileBlock);
            return writeMessage;
        } else if (message instanceof WriteConfigurationBlockMessage) {
            return new CloseWriteSessionMessage();
        } else if (message instanceof CloseWriteSessionMessage) finish(null);
        return null;
    }
}
