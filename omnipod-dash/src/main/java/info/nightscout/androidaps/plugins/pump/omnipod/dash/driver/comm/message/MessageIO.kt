package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.UnexpectedCommandException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadJoiner
import info.nightscout.androidaps.utils.extensions.toHex

class MessageIO(private val aapsLogger: AAPSLogger, private val bleIO: BleIO) {

    fun sendMessage(msg: MessagePacket) {
        bleIO.flushIncomingQueues()
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandRTS().data)
        val expectCTS = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectCTS) != BleCommandCTS()) {
            throw UnexpectedCommandException(BleCommand(expectCTS))
        }
        val payload = msg.asByteArray()
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending message: ${payload.toHex()}")
        val splitter = PayloadSplitter(payload)
        val packets = splitter.splitInPackets()
        for (packet in packets) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Sending DATA: ${packet.asByteArray().toHex()}")
            bleIO.sendAndConfirmPacket(CharacteristicType.DATA, packet.asByteArray())
        }
        // TODO: peek for NACKs
        val expectSuccess = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectSuccess) != BleCommandSuccess()) {
            throw UnexpectedCommandException(BleCommand(expectSuccess))
        }
        // TODO: handle NACKS/FAILS/etc
    }

    fun receiveMessage(): MessagePacket {
        val expectRTS = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectRTS) != BleCommandRTS()) {
            throw UnexpectedCommandException(BleCommand(expectRTS))
        }
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandCTS().data)
        try {
            val joiner = PayloadJoiner(bleIO.receivePacket(CharacteristicType.DATA))
            for (i in 1 until joiner.fullFragments + 1) {
                joiner.accumulate(bleIO.receivePacket(CharacteristicType.DATA))
            }
            if (joiner.oneExtraPacket) {
                joiner.accumulate(bleIO.receivePacket(CharacteristicType.DATA))
            }
            val fullPayload = joiner.finalize()
            bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandSuccess().data)
            return MessagePacket.parse(fullPayload)
        } catch (e: IncorrectPacketException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Received incorrect packet: $e")
            bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandNack(e.expectedIndex).data)
            throw MessageIOException(cause = e)
        } catch (e: CrcMismatchException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "CRC mismatch: $e")
            bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandFail().data)
            throw MessageIOException(cause = e)
        }
    }
}
