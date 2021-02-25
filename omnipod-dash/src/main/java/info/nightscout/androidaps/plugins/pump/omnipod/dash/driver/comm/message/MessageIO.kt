package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.BleManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommand
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandCTS
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandHello
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandRTS
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.UnexpectedCommandException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.PayloadSplitter

class MessageIO(private val aapsLogger: AAPSLogger, private val bleIO: BleIO) {
    fun sendMesssage(msg: Message) {
        bleIO.flushIncomingQueues();
        bleIO.sendAndConfirmPacket(CharacteristicType.CMD, BleCommandRTS().data)
        val expectCTS = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectCTS) != BleCommandCTS()) {
            throw UnexpectedCommandException(BleCommand(expectCTS))
        }
        val payload = msg.asByteArray()
        val splitter = PayloadSplitter(payload)
        val packets = splitter.splitInPackets()
        for (packet in packets) {
            bleIO.sendAndConfirmPacket(CharacteristicType.DATA, packet.asByteArray())
        }
        // TODO: peek for NACKs
        val expectSuccess = bleIO.receivePacket(CharacteristicType.CMD)
        if (BleCommand(expectSuccess) != BleCommandCTS()) {
            throw UnexpectedCommandException(BleCommand(expectSuccess))
        }
        // TODO: handle NACKS/FAILS/etc
        bleIO.flushIncomingQueues();
    }


    fun receiveMessage(): Message? {
        // do the RTS/CTS/data/success dance
        return null
    }
}