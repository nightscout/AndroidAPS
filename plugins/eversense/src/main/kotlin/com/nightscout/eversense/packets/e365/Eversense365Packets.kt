package com.nightscout.eversense.packets.e365

class Eversense365Packets {
    companion object {
        const val AuthenticateCommandId = 0x09.toByte()
        const val AuthenticateResponseId = 0x0B.toByte()

        const val AuthenticateWhoAmI = 0x01.toByte()
        const val AuthenticateIdentity = 0x02.toByte()
        const val AuthenticateStart = 0x03.toByte()
    }
}