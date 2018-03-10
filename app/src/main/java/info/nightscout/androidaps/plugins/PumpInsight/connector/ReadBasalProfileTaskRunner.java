package info.nightscout.androidaps.plugins.PumpInsight.connector;

import android.util.Log;

import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ActiveProfileBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile1Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile2Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile3Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile4Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile5Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfileBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ConfigurationBlock;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.configuration.ReadConfigurationBlockMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

/**
 * Created by Tebbe Ubben on 10.03.2018.
 */

public class ReadBasalProfileTaskRunner extends TaskRunner {

    public ReadBasalProfileTaskRunner(SightServiceConnector serviceConnector) {
        super(serviceConnector);
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) {
            ReadConfigurationBlockMessage readMessage = new ReadConfigurationBlockMessage();
            readMessage.setConfigurationBlockID(ActiveProfileBlock.ID);
            return readMessage;
        } else if (message instanceof ReadConfigurationBlockMessage) {
            ConfigurationBlock configurationBlock = ((ReadConfigurationBlockMessage) message).getConfigurationBlock();
            if (configurationBlock instanceof ActiveProfileBlock) {
                ActiveProfileBlock activeProfileBlock = (ActiveProfileBlock) configurationBlock;
                ReadConfigurationBlockMessage readMessage = new ReadConfigurationBlockMessage();
                switch (activeProfileBlock.getActiveProfile()) {
                    case BR_PROFILE_1:
                        readMessage.setConfigurationBlockID(BRProfile1Block.ID);
                        break;
                    case BR_PROFILE_2:
                        readMessage.setConfigurationBlockID(BRProfile2Block.ID);
                        break;
                    case BR_PROFILE_3:
                        readMessage.setConfigurationBlockID(BRProfile3Block.ID);
                        break;
                    case BR_PROFILE_4:
                        readMessage.setConfigurationBlockID(BRProfile4Block.ID);
                        break;
                    case BR_PROFILE_5:
                        readMessage.setConfigurationBlockID(BRProfile5Block.ID);
                        break;
                }
                ReadConfigurationBlockMessage test = new ReadConfigurationBlockMessage();
                test.setConfigurationBlockID(BRProfile1Block.ID);
                return test;
            } else if (configurationBlock instanceof BRProfileBlock) {
                finish(((BRProfileBlock) configurationBlock).getProfileBlocks());
            }
        }
        return null;
    }
}
