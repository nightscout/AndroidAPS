package app.aaps.pump.danar.comm

interface MessageHashTableBase {

    fun put(message: MessageBase)

    /** The message for [command], or one that only logs it, with the received command so the log can name it. */
    fun findMessage(command: Int): MessageBase
}
