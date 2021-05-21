package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BasalProfileBlock
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf
import java.util.*

abstract class BRProfileBlock : ParameterBlock() {

    internal lateinit var profileBlocks: MutableList<BasalProfileBlock>
    override fun parse(byteBuf: ByteBuf) {
        val newProfileBlocks = mutableListOf<BasalProfileBlock>()
        for (i in 0..23) {
            val basalProfileBlock = BasalProfileBlock()
            basalProfileBlock.duration = byteBuf.readUInt16LE()
            newProfileBlocks.add(basalProfileBlock)
        }
        for (i in 0..23) newProfileBlocks.get(i).basalAmount = byteBuf.readUInt16Decimal()
        val iterator = newProfileBlocks.iterator()
        while (iterator.hasNext()) if (iterator.next().duration == 0) iterator.remove()
        profileBlocks = newProfileBlocks
    }

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(96)
            for (i in 0..23) {
                if (profileBlocks.size > i) byteBuf.putUInt16LE(profileBlocks[i].duration) else byteBuf.putUInt16LE(0)
            }
            for (i in 0..23) {
                if (profileBlocks.size > i) byteBuf.putUInt16Decimal(profileBlocks[i].basalAmount) else byteBuf.putUInt16Decimal(0.0)
            }
            return byteBuf
        }

    fun getProfileBlocks(): List<BasalProfileBlock> {
        return profileBlocks
    }

    fun setProfileBlocks(profileBlocks: MutableList<BasalProfileBlock>) {
        this.profileBlocks = profileBlocks
    }
}