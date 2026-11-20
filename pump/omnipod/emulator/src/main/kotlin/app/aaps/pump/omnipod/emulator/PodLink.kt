package app.aaps.pump.omnipod.emulator

import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.DataBleIO
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * One Bluetooth link between the phone and the emulated pod: the CMD and the DATA characteristic, each in both
 * directions. What one side writes, the other side reads. A link that went down stays down; a new connect makes a new one.
 */
internal class PodLink {

    val cmdToPod: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    val cmdToPhone: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    val dataToPod: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    val dataToPhone: BlockingQueue<ByteArray> = LinkedBlockingQueue()

    @Volatile var up = true
}

/** The state of the link as the driver's pod state keeps it. The real connection writes the same three. */
enum class PodLinkState { CONNECTING, CONNECTED, DISCONNECTED }

/** One end of one characteristic. A write on a link that is down fails, and a read on it gets nothing. */
internal open class PipeIO(
    private val link: PodLink,
    protected val incoming: BlockingQueue<ByteArray>,
    private val outgoing: BlockingQueue<ByteArray>
) : BleCharacteristicIO {

    override fun receivePacket(timeoutMs: Long): ByteArray? {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        try {
            while (link.up) {
                val left = end - System.nanoTime()
                if (left <= 0) return null
                // In slices, so a read ends soon after the link went down.
                incoming.poll(min(left, SLICE_NANOS), TimeUnit.NANOSECONDS)?.let { return it }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return null
    }

    override fun sendAndConfirmPacket(payload: ByteArray): BleSendResult =
        if (link.up) {
            outgoing.put(payload.copyOf())
            BleSendSuccess
        } else BleSendErrorSending("The link is down")

    override fun flushIncomingQueue(): Boolean {
        var foundRts = false
        while (true) {
            val packet = incoming.poll() ?: break
            if (packet.isNotEmpty() && packet[0] == BleCommandRTS.data[0]) foundRts = true
        }
        return foundRts
    }

    override fun readyToRead(): BleSendResult = BleSendSuccess

    private companion object {

        val SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(50)
    }
}

/** The CMD characteristic, with the flow control helpers of the driver's own `CmdBleIO`. */
internal class PipeCmdIO(link: PodLink, incoming: BlockingQueue<ByteArray>, outgoing: BlockingQueue<ByteArray>) :
    PipeIO(link, incoming, outgoing), CmdBleIO {

    override fun peekCommand(): ByteArray? = incoming.peek()

    override fun hello(): BleSendResult = sendAndConfirmPacket(BleCommandHello(OmnipodDashBleManagerImpl.CONTROLLER_ID).data)

    override fun expectCommandType(expected: BleCommand, timeoutMs: Long): BleConfirmResult {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            val leftMs = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime())
            if (leftMs <= 0) return BleConfirmError("Error reading packet")
            val received = receivePacket(leftMs) ?: return BleConfirmError("Error reading packet")
            when {
                received.isEmpty()                              -> return BleConfirmIncorrectData(received)
                received[0] == expected.data[0]                 -> return BleConfirmSuccess
                received[0] == BleCommandType.PAIR_STATUS.value -> Unit
                else                                            -> return BleConfirmIncorrectData(received)
            }
        }
    }
}

/** The DATA characteristic. */
internal class PipeDataIO(link: PodLink, incoming: BlockingQueue<ByteArray>, outgoing: BlockingQueue<ByteArray>) :
    PipeIO(link, incoming, outgoing), DataBleIO
