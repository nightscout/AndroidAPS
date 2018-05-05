package info.nightscout.androidaps.plugins.PumpInsight.connector;

import java.util.List;

import sugar.free.sightparser.applayer.descriptors.ActiveBolus;
import sugar.free.sightparser.applayer.descriptors.PumpStatus;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ActiveProfileBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile1Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile2Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile3Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile4Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfile5Block;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfileBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ConfigurationBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.MaxBRAmountBlock;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.MaxBolusAmountBlock;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.configuration.ReadConfigurationBlockMessage;
import sugar.free.sightparser.applayer.messages.status.ActiveBolusesMessage;
import sugar.free.sightparser.applayer.messages.status.BatteryAmountMessage;
import sugar.free.sightparser.applayer.messages.status.CartridgeAmountMessage;
import sugar.free.sightparser.applayer.messages.status.CurrentBasalMessage;
import sugar.free.sightparser.applayer.messages.status.CurrentTBRMessage;
import sugar.free.sightparser.applayer.messages.status.PumpStatusMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

/**
 * Created by Tebbe Ubben on 12.03.2018.
 */

public class StatusTaskRunner extends TaskRunner {

    private Result result = new Result();

    public StatusTaskRunner(SightServiceConnector serviceConnector) {
        super(serviceConnector);
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new PumpStatusMessage();
        else if (message instanceof PumpStatusMessage) {
            result.pumpStatus = ((PumpStatusMessage) message).getPumpStatus();
            if (result.pumpStatus == PumpStatus.STOPPED) return new BatteryAmountMessage();
            else return new CurrentTBRMessage();
        } else if (message instanceof CurrentTBRMessage) {
            CurrentTBRMessage currentTBRMessage = (CurrentTBRMessage) message;
            result.tbrAmount = currentTBRMessage.getPercentage();
            result.tbrInitialDuration = currentTBRMessage.getInitialTime();
            result.tbrLeftoverDuration = currentTBRMessage.getLeftoverTime();
            return new ActiveBolusesMessage();
        } else if (message instanceof ActiveBolusesMessage) {
            ActiveBolusesMessage activeBolusesMessage = (ActiveBolusesMessage) message;
            result.activeBolus1 = activeBolusesMessage.getBolus1();
            result.activeBolus2 = activeBolusesMessage.getBolus2();
            result.activeBolus3 = activeBolusesMessage.getBolus3();
            return new CurrentBasalMessage();
        } else if (message instanceof CurrentBasalMessage) {
            result.baseBasalRate = ((CurrentBasalMessage) message).getCurrentBasalAmount();
            return new BatteryAmountMessage();
        } else if (message instanceof BatteryAmountMessage) {
            result.battery = ((BatteryAmountMessage) message).getBatteryAmount();
            return new CartridgeAmountMessage();
        } else if (message instanceof CartridgeAmountMessage) {
            result.cartridge = ((CartridgeAmountMessage) message).getCartridgeAmount();
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
                return readMessage;
            } else if (configurationBlock instanceof BRProfileBlock) {
                result.basalProfile = ((BRProfileBlock) configurationBlock).getProfileBlocks();
                ReadConfigurationBlockMessage readMessage = new ReadConfigurationBlockMessage();
                readMessage.setConfigurationBlockID(MaxBolusAmountBlock.ID);
                return readMessage;
            } else if (configurationBlock instanceof MaxBolusAmountBlock) {
                result.maximumBolusAmount = ((MaxBolusAmountBlock) configurationBlock).getMaximumAmount();
                ReadConfigurationBlockMessage readMessage = new ReadConfigurationBlockMessage();
                readMessage.setConfigurationBlockID(MaxBRAmountBlock.ID);
                return readMessage;
            } else if (configurationBlock instanceof MaxBRAmountBlock) {
                result.maximumBasalAmount = ((MaxBRAmountBlock) configurationBlock).getMaximumAmount();
                finish(result);
            }
        }
        return null;
    }

    public static class Result {
        public PumpStatus pumpStatus;
        public double baseBasalRate;
        public int battery;
        public double cartridge ;
        public int tbrAmount = 100;
        public int tbrInitialDuration = 0;
        public int tbrLeftoverDuration = 0;
        public ActiveBolus activeBolus1;
        public ActiveBolus activeBolus2;
        public ActiveBolus activeBolus3;
        public List<BRProfileBlock.ProfileBlock> basalProfile;
        public double maximumBolusAmount;
        public double maximumBasalAmount;
    }
}
