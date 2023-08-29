package info.nightscout.comboctl.base.testUtils

import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.BluetoothDevice
import info.nightscout.comboctl.base.ComboFrameParser
import info.nightscout.comboctl.base.ComboIO
import info.nightscout.comboctl.base.byteArrayListOfInts
import info.nightscout.comboctl.base.toComboFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

class TestBluetoothDevice(private val testComboIO: ComboIO) : BluetoothDevice(Dispatchers.IO) {
    private val frameParser = ComboFrameParser()
    private var innerJob = SupervisorJob()
    private var innerScope = CoroutineScope(innerJob)

    override val address: BluetoothAddress = BluetoothAddress(byteArrayListOfInts(1, 2, 3, 4, 5, 6))

    override fun connect() {
    }

    override fun disconnect() {
        // Synchronized rest so we don't interfere with pushing/parsing data into the frameParser.
        synchronized(frameParser) { frameParser.reset() }
        runBlocking {
            innerJob.cancelAndJoin()
        }

        // Reinitialize these, since once a Job is cancelled, it cannot be reused again.
        innerJob = SupervisorJob()
        innerScope = CoroutineScope(innerJob)
    }

    override fun unpair() {
    }

    override fun blockingSend(dataToSend: List<Byte>) {
        synchronized(frameParser) {
            frameParser.pushData(dataToSend)
            frameParser.parseFrame()
        }?.let {
            runBlocking {
                innerScope.async {
                    testComboIO.send(it)
                }.await()
            }
        }
    }

    override fun blockingReceive(): List<Byte> = runBlocking {
        innerScope.async {
            val retval = testComboIO.receive().toComboFrame()
            retval
        }.await()
    }
}
