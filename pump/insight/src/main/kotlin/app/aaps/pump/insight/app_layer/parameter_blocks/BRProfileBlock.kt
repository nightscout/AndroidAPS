package app.aaps.pump.insight.app_layer.parameter_blocks

import app.aaps.pump.insight.descriptors.BasalProfileBlock
import app.aaps.pump.insight.utils.ByteBuf

abstract class BRProfileBlock : ParameterBlock() {

    internal lateinit var profileBlocks: MutableList<BasalProfileBlock>
    override fun parse(byteBuf: ByteBuf) {
        profileBlocks = mutableListOf<BasalProfileBlock>()
        profileBlocks.let {
            for (i in 0..23) {
                val basalProfileBlock = BasalProfileBlock()
                basalProfileBlock.duration = byteBuf.readUInt16LE()
                it.add(basalProfileBlock)
            }
            for (i in 0..23) it[i].basalAmount = byteBuf.readUInt16Decimal()
            val iterator = it.iterator()
            while (iterator.hasNext()) if (iterator.next().duration == 0) iterator.remove()
        }
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