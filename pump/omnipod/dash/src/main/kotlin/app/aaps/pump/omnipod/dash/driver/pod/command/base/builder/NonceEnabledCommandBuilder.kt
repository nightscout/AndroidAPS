package app.aaps.pump.omnipod.dash.driver.pod.command.base.builder

import app.aaps.pump.omnipod.dash.driver.pod.command.base.Command

@Suppress("UNCHECKED_CAST")
abstract class NonceEnabledCommandBuilder<T : NonceEnabledCommandBuilder<T, R>, R : Command> : HeaderEnabledCommandBuilder<T, R>() {

    protected var nonce: Int? = null
    override fun build(): R {
        requireNotNull(nonce) { "nonce can not be null" }
        return super.build()
    }

    fun setNonce(nonce: Int): T {
        this.nonce = nonce
        return this as T
    }
}
