package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex
import com.google.crypto.tink.subtle.Hex
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MessagePacketTest {

    private val payload =
        "54,57,11,01,07,00,03,40,08,20,2e,a8,08,20,2e,a9,ab,35,d8,31,60,9b,b8,fe,3a,3b,de,5b,18,37,24,9a,16,db,f8,e4,d3,05,e9,75,dc,81,7c,37,07,cc,41,5f,af,8a".replace(
            ",",
            ""
        )

    @Test fun testParseMessagePacket() {
        val msg = MessagePacket.parse(Hex.decode(payload))
        Assertions.assertEquals(msg.type, MessageType.ENCRYPTED)
        Assertions.assertEquals(msg.source, Id.fromLong(136326824))
        Assertions.assertEquals(msg.destination, Id.fromLong(136326825))
        Assertions.assertEquals(msg.sequenceNumber, 7.toByte())
        Assertions.assertEquals(msg.ackNumber, 0.toByte())
        Assertions.assertEquals(msg.eqos, 1.toShort())
        Assertions.assertEquals(msg.priority, false)
        Assertions.assertEquals(msg.lastMessage, false)
        Assertions.assertEquals(msg.gateway, false)
        Assertions.assertEquals(msg.sas, true)
        Assertions.assertEquals(msg.tfs, false)
        Assertions.assertEquals(msg.version, 0.toShort())
        Assertions.assertEquals(msg.payload.toHex(), payload.substring(32, payload.length))
    }

    @Test fun testSerializeMessagePacket() {
        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            source = Id.fromLong(136326824),
            destination = Id.fromLong(136326825),
            sequenceNumber = 7.toByte(),
            ackNumber = 0.toByte(),
            eqos = 1.toShort(),
            priority = false,
            lastMessage = false,
            gateway = false,
            sas = true,
            tfs = false,
            payload = Hex.decode(payload.substring(32, payload.length))
        )
        Assertions.assertEquals(msg.asByteArray().toHex(), payload)
    }
}
