package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.*

enum class ParameterBlocks (val id: Int, val type: Class<out ParameterBlock?>)  {
    FACTORYMAXBOLUSAMOUNTBLOCK (41222, FactoryMaxBolusAmountBlock::class.java),
    MAXBOLUSAMOUNTBLOCK (31, MaxBolusAmountBlock::class.java),
    FACTORYMINBOLUSAMOUNTBLOCK (60183, FactoryMinBolusAmountBlock::class.java),
    SYSTEMIDENTIFICATIONBLOCK (35476, SystemIdentificationBlock::class.java),
    BRPROFILE1BLOCK (7136, BRProfile1Block::class.java),
    BRPROFILE2BLOCK (7167, BRProfile2Block::class.java),
    BRPROFILE3BLOCK (7532, BRProfile3Block::class.java),
    BRPROFILE4BLOCK (7539, BRProfile4Block::class.java),
    BRPROFILE5BLOCK (7567, BRProfile5Block::class.java),
    BRPROFILE1NAMEBLOCK (48265, BRProfile1NameBlock::class.java),
    BRPROFILE2NAMEBLOCK (48278, BRProfile2NameBlock::class.java),
    BRPROFILE3NAMEBLOCK (48975, BRProfile3NameBlock::class.java),
    BRPROFILE4NAMEBLOCK (48976, BRProfile4NameBlock::class.java),
    BRPROFILE5NAMEBLOCK (49068, BRProfile5NameBlock::class.java),
    ACTIVEBRPROFILEBLOCK (7568, ActiveBRProfileBlock::class.java),
    MAXBASALAMOUNTBLOCK (6940, MaxBasalAmountBlock::class.java),
    FACTORYMINBASALAMOUNTBLOCK (60395, FactoryMinBasalAmountBlock::class.java),
    FACTORYMAXBASALAMOUNTBLOCK (41241, FactoryMaxBasalAmountBlock::class.java),
    TBROVERNOTIFICATIONBLOCK (25814, TBROverNotificationBlock::class.java);

    companion object {
        fun fromType(type: Class<out ParameterBlock?>) = values().firstOrNull { it.type == type }
        fun fromId(id: Int) = values().firstOrNull { it.id == id }

    }
}