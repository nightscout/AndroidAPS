package app.aaps.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.Id
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Hex
import org.junit.jupiter.api.Test

class MessagePacketTest {

    private val payload =
        "54,57,11,01,07,00,03,40,08,20,2e,a8,08,20,2e,a9,ab,35,d8,31,60,9b,b8,fe,3a,3b,de,5b,18,37,24,9a,16,db,f8,e4,d3,05,e9,75,dc,81,7c,37,07,cc,41,5f,af,8a".replace(
            ",",
            ""
        )

    @Test fun testParseMessagePacket() {
        val msg = MessagePacket.parse(Hex.decode(payload))
        assertThat(msg.type).isEqualTo(MessageType.ENCRYPTED)
        assertThat(msg.source).isEqualTo(Id.fromLong(136326824))
        assertThat(msg.destination).isEqualTo(Id.fromLong(136326825))
        assertThat(msg.sequenceNumber).isEqualTo(7.toByte())
        assertThat(msg.ackNumber).isEqualTo(0.toByte())
        assertThat(msg.eqos).isEqualTo(1.toShort())
        assertThat(msg.priority).isFalse()
        assertThat(msg.lastMessage).isFalse()
        assertThat(msg.gateway).isFalse()
        assertThat(msg.sas).isTrue()
        assertThat(msg.tfs).isFalse()
        assertThat(msg.version).isEqualTo(0.toShort())
        assertThat(payload.substring(32, payload.length)).isEqualTo(msg.payload.toHex())
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
        assertThat(msg.asByteArray().toHex()).isEqualTo(payload)
    }
}
