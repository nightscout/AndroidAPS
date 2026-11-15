package app.aaps.pump.danar.comm

interface MessageHashTableBase {

    fun put(message: MessageBase)
    fun findMessage(command: Int): MessageBase
}
