package app.aaps.pump.insight.descriptors

import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile1Block
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile1NameBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile2Block
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile2NameBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile3Block
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile3NameBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile4Block
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile4NameBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile5Block
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile5NameBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMaxBasalAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMaxBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMinBasalAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMinBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.MaxBasalAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.MaxBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.ParameterBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.SystemIdentificationBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock

enum class ParameterBlocks(val id: Int, val type: Class<out ParameterBlock?>) {
    FACTORYMAXBOLUSAMOUNTBLOCK(41222, FactoryMaxBolusAmountBlock::class.java),
    MAXBOLUSAMOUNTBLOCK(31, MaxBolusAmountBlock::class.java),
    FACTORYMINBOLUSAMOUNTBLOCK(60183, FactoryMinBolusAmountBlock::class.java),
    SYSTEMIDENTIFICATIONBLOCK(35476, SystemIdentificationBlock::class.java),
    BRPROFILE1BLOCK(7136, BRProfile1Block::class.java),
    BRPROFILE2BLOCK(7167, BRProfile2Block::class.java),
    BRPROFILE3BLOCK(7532, BRProfile3Block::class.java),
    BRPROFILE4BLOCK(7539, BRProfile4Block::class.java),
    BRPROFILE5BLOCK(7567, BRProfile5Block::class.java),
    BRPROFILE1NAMEBLOCK(48265, BRProfile1NameBlock::class.java),
    BRPROFILE2NAMEBLOCK(48278, BRProfile2NameBlock::class.java),
    BRPROFILE3NAMEBLOCK(48975, BRProfile3NameBlock::class.java),
    BRPROFILE4NAMEBLOCK(48976, BRProfile4NameBlock::class.java),
    BRPROFILE5NAMEBLOCK(49068, BRProfile5NameBlock::class.java),
    ACTIVEBRPROFILEBLOCK(7568, app.aaps.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock::class.java),
    MAXBASALAMOUNTBLOCK(6940, MaxBasalAmountBlock::class.java),
    FACTORYMINBASALAMOUNTBLOCK(60395, FactoryMinBasalAmountBlock::class.java),
    FACTORYMAXBASALAMOUNTBLOCK(41241, FactoryMaxBasalAmountBlock::class.java),
    TBROVERNOTIFICATIONBLOCK(25814, TBROverNotificationBlock::class.java);

    companion object {

        fun fromType(type: Class<out ParameterBlock?>) = ParameterBlocks.entries.firstOrNull { it.type == type }
        fun fromId(id: Int) = ParameterBlocks.entries.firstOrNull { it.id == id }

    }
}