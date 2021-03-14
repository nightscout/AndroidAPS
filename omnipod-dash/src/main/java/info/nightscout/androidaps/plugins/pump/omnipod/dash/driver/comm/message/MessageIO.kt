package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.UnexpectedCommandException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadJoiner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.BlePacket
import info.nightscout.androidaps.utils.extensions.toHex

class MessageIO(private val aapsLogger: AAPSLogger, private val bleIO: BleIO) {

    private fun expectCommandType(actual: BleCommand, expected: BleCommand) {
        if (actual.data.isEmpty()) {
            throw UnexpectedCommandException(actual)
        }
        // first byte is the command type
        if (actual.data[0] == expected.data[0]) {
            return
        }
        throw UnexpectedCommandException(actual)
    }

    private fun peekForNack(index: Int, packets: List<BlePacket>) {
        val peekCmd = bleIO.peekCommand() ?: return

        if (peekCmd.isEmpty()) {
            throw UnexpectedCommandException(BleCommand(peekCmd))
        }
        when (BleCommandType.byValue(peekCmd[0])) {
            BleCommandType.NACK -> {
                if (peekCmd.size < 2) {
                    throw UnexpectedCommandException(BleCommand(peekCmd))
                }
                val missingIdx = peekCmd[1]
                if (missingIdx > packets.size) {
                    throw UnexpectedCommandException(BleCommand(peekCmd))

                }
                bleIO.receivePacket(CharacteristicType.CMD) //consume NACK
                bleIO.sendAndConfirmPacket(CharacteristicType.DATA, packets[missingIdx.toInt()].toByteArray())
            }

            BleCommandType.SUCCESS -> {
                if (index != packets.size - 1) {
                    throw UnexpectedCommandException(BleCommand(peekCmd))
                }
            }

            else ->
                throw UnexpectedCommandException(BleCommand(peekCmd))
        }
    }

    fun sendMessage(msg: MessagePacket) {
        bleIO.flushIncomingQueues()
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandRTS().data)
        val expectCTS = bleIO.receivePacket(CharacteristicType.CMD)
        expectCommandType(BleCommand(expectCTS), BleCommandCTS())
        val payload = msg.asByteArray()
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending message: ${payload.toHex()}")
        val splitter = PayloadSplitter(payload)
        val packets = splitter.splitInPackets()
        for ((index, packet) in packets.withIndex()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Sending DATA: ${packet.toByteArray().toHex()}")
            bleIO.sendAndConfirmPacket(CharacteristicType.DATA, packet.toByteArray())
            peekForNack(index, packets)
            // This is implementing the same logic as the PDM.
            // I think it wil not work in case of packet lost.
            // This is because each lost packet, we will receive a NACK on the next packet.
            // At the end, we will still be missing the last packet(s).
            // I don't worry too much about this because for commands we have retries implemented at MessagePacket level anyway
            // If this will be a problem in the future, the fix might be(pending testing with a real pod) to move back the index
            // at the value received in the NACK and make sure don't retry forever.
        }
        val expectSuccess = bleIO.receivePacket(CharacteristicType.CMD)
        expectCommandType(BleCommand(expectSuccess), BleCommandSuccess())
    }

    fun receiveMessage(): MessagePacket {
        val expectRTS = bleIO.receivePacket(CharacteristicType.CMD, MESSAGE_READ_TIMEOUT_MS)
        expectCommandType(BleCommand(expectRTS), BleCommandRTS())
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

    companion object {
        private const val MESSAGE_READ_TIMEOUT_MS = 4000.toLong()
    }
}
