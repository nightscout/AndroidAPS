package app.aaps.pump.omnipod.dash.driver.pod.command.base.builder

import app.aaps.pump.omnipod.dash.driver.pod.command.base.Command

@Suppress("UNCHECKED_CAST")
abstract class HeaderEnabledCommandBuilder<T : HeaderEnabledCommandBuilder<T, R>, R : Command> : CommandBuilder<R> {

    protected var uniqueId: Int? = null
    protected var sequenceNumber: Short? = null
    protected var multiCommandFlag = false

    override fun build(): R {
        requireNotNull(uniqueId) { "uniqueId can not be null" }
        requireNotNull(sequenceNumber) { "sequenceNumber can not be null" }
        return buildCommand()
    }

    fun setUniqueId(uniqueId: Int): T {
        this.uniqueId = uniqueId
        return this as T
    }

    fun setSequenceNumber(sequenceNumber: Short): T {
        this.sequenceNumber = sequenceNumber
        return this as T
    }

    fun setMultiCommandFlag(multiCommandFlag: Boolean): T {
        this.multiCommandFlag = multiCommandFlag
        return this as T
    }

    protected abstract fun buildCommand(): R
}
