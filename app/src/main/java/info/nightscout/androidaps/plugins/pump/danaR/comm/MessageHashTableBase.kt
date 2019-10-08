package info.nightscout.androidaps.plugins.pump.danaR.comm

interface MessageHashTableBase {
    fun put(message: MessageBase)
    fun findMessage(command: Int): MessageBase
}
