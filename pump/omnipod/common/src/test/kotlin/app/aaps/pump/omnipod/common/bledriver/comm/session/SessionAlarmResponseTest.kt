package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding
import app.aaps.pump.omnipod.common.bledriver.comm.pair.CommandSigner
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SessionAlarmResponseTest {

    private val msgIO: MessageIO = mock()
    private val enDecrypt: EnDecrypt = mock()

    // The reported Dash fault response, with a test pod ID in the envelope.
    private val dashAlarm = "0000000214180216020d0000000e003e10000403ff0004000008000000000000"
    private val o5Alarm = "002a1c6e301802160209020000010c92000000030d0cc8000000000070410258"

    @Test
    fun `a Dash alarm is a received response so status processing can reach the notification`() {
        val result = session(dashAlarm).readAndAckResponse()

        val response = assertInstanceOf<AlarmStatusResponse>(assertInstanceOf<CommandReceiveSuccess>(result).result)
        assertThat(response.alarmType).isEqualTo(AlarmType.ALARM_SAWCOP_RESET)
        assertThat(response.podStatus).isEqualTo(PodStatus.ALARM)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.SUSPENDED)
        assertAckSent()
    }

    @Test
    fun `an Omnipod 5 alarm still fails the command and carries the fault response`() {
        val result = session(o5Alarm, commandSigner = mock()).readAndAckResponse()

        assertInstanceOf<AlarmStatusResponse>(assertInstanceOf<CommandReceiveError>(result).result)
        assertAckSent()
    }

    @Test
    fun `a Dash alarm is preserved when sending its ACK fails`() {
        val session = session(dashAlarm)
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendErrorSending("Test ACK failure"))

        val result = session.readAndAckResponse()

        val response = assertInstanceOf<AlarmStatusResponse>(assertInstanceOf<CommandAckError>(result).result)
        assertThat(response.alarmType).isEqualTo(AlarmType.ALARM_SAWCOP_RESET)
        assertAckSent()
    }

    @Test
    fun `a missing response still fails without a fault response`() {
        val session = session(dashAlarm)
        whenever(msgIO.receiveMessage()).thenReturn(null)

        val result = assertInstanceOf<CommandReceiveError>(session.readAndAckResponse())

        assertThat(result.result).isNull()
    }

    private fun session(envelopeHex: String, commandSigner: CommandSigner? = null): Session {
        val envelope = ByteArray(envelopeHex.length / 2) { envelopeHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val ids = Ids.forController(Id.fromInt(1), Id.fromInt(2))
        val packet = MessagePacket(
            type = MessageType.ENCRYPTED,
            source = ids.podId,
            destination = ids.myId,
            payload = StringLengthPrefixEncoding.formatKeys(arrayOf("0.0="), arrayOf(envelope)),
            sequenceNumber = 2
        )
        whenever(msgIO.receiveMessage()).thenReturn(packet)
        whenever(enDecrypt.decrypt(packet)).thenReturn(packet)
        whenever(enDecrypt.encrypt(any())).thenAnswer { it.getArgument<MessagePacket>(0) }
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        val keys = SessionKeys(ByteArray(16), Nonce(ByteArray(8), 0), 1)
        return Session(AAPSLoggerTest(), msgIO, ids, keys, enDecrypt, commandSigner)
    }

    private fun assertAckSent() {
        val ack = argumentCaptor<MessagePacket>()
        verify(msgIO).sendMessage(ack.capture())
        assertThat(ack.firstValue.ack).isTrue()
        assertThat(ack.firstValue.ackNumber.toInt()).isEqualTo(3)
    }
}
