package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.UnexpectedCommandException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadJoiner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadJoinerActionAccept
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadJoinerActionReject
import info.nightscout.androidaps.utils.extensions.toHex

class MessageIO(private val aapsLogger: AAPSLogger, private val bleIO: BleIO) {

    fun sendMesssage(msg: MessagePacket) {
        bleIO.flushIncomingQueues()
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandRTS().data)
        val expectCTS = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectCTS) != BleCommandCTS()) {
            throw UnexpectedCommandException(BleCommand(expectCTS))
        }
        val payload = msg.asByteArray()
        val splitter = PayloadSplitter(payload)
        val packets = splitter.splitInPackets()
        for (packet in packets) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Sending DATA: ", packet.asByteArray().toHex())
            bleIO.sendAndConfirmPacket(CharacteristicType.DATA, packet.asByteArray())
        }
        // TODO: peek for NACKs
        val expectSuccess = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectSuccess) != BleCommandSuccess()) {
            throw UnexpectedCommandException(BleCommand(expectSuccess))
        }
        // TODO: handle NACKS/FAILS/etc
        bleIO.flushIncomingQueues()
    }

    fun receiveMessage(): MessagePacket {
        val expectRTS = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectRTS) != BleCommandCTS()) {
            throw UnexpectedCommandException(BleCommand(expectRTS))
        }
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandCTS().data)
        val joiner = PayloadJoiner()
        var data = bleIO.receivePacket(CharacteristicType.DATA)
        val fragments = joiner.start(data)
        for (i in 1 until fragments) {
            data = bleIO.receivePacket(CharacteristicType.DATA)
            val accumlateAction = joiner.accumulate(data)
            if (accumlateAction is PayloadJoinerActionReject) {
                bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandNack(accumlateAction.idx).data)
            }
        }
        if (joiner.oneExtra) {
            var data = bleIO.receivePacket(CharacteristicType.DATA)
            val accumulateAction = joiner.accumulate(data)
            if (accumulateAction is PayloadJoinerActionReject) {
                bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandNack(accumulateAction.idx).data)
            }
        }
        val finalCmd = when (joiner.finalize()) {
            is PayloadJoinerActionAccept -> BleCommandSuccess()
            is PayloadJoinerActionReject -> BleCommandFail()
        }
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, finalCmd.data)
        val fullPayload = joiner.bytes()
        return MessagePacket.parse(fullPayload)
    }
}