package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.FactoryMaxBasalAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.FactoryMinBasalAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.MaxBasalAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ParameterBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile1Block;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile1NameBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile2Block;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile2NameBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile3Block;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile3NameBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile4Block;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile4NameBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile5Block;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile5NameBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.FactoryMaxBolusAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.FactoryMinBolusAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.MaxBolusAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.SystemIdentificationBlock;

public class ParameterBlockIDs {

    public static final IDStorage<Class<? extends ParameterBlock>, Integer> IDS = new IDStorage<>();

    static {
        IDS.put(FactoryMaxBolusAmountBlock.class, 41222);
        IDS.put(MaxBolusAmountBlock.class, 31);
        IDS.put(FactoryMinBolusAmountBlock.class, 60183);
        IDS.put(SystemIdentificationBlock.class, 35476);
        IDS.put(BRProfile1Block.class, 7136);
        IDS.put(BRProfile2Block.class, 7167);
        IDS.put(BRProfile3Block.class, 7532);
        IDS.put(BRProfile4Block.class, 7539);
        IDS.put(BRProfile5Block.class, 7567);
        IDS.put(BRProfile1NameBlock.class, 48265);
        IDS.put(BRProfile2NameBlock.class, 48278);
        IDS.put(BRProfile3NameBlock.class, 48975);
        IDS.put(BRProfile4NameBlock.class, 48976);
        IDS.put(BRProfile5NameBlock.class, 49068);
        IDS.put(ActiveBRProfileBlock.class, 7568);
        IDS.put(MaxBasalAmountBlock.class, 6940);
        IDS.put(FactoryMinBasalAmountBlock.class, 60395);
        IDS.put(FactoryMaxBasalAmountBlock.class, 41241);
        IDS.put(TBROverNotificationBlock.class, 25814);
    }

}
