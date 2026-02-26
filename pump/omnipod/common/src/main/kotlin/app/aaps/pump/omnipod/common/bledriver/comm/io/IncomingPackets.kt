package app.aaps.pump.omnipod.common.bledriver.comm.io

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

class IncomingPackets {

    val cmdQueue: BlockingQueue<ByteArray> = LinkedBlockingDeque()
    val dataQueue: BlockingQueue<ByteArray> = LinkedBlockingDeque()

    fun byCharacteristicType(char: CharacteristicType): BlockingQueue<ByteArray> {
        return when (char) {
            CharacteristicType.DATA -> dataQueue
            CharacteristicType.CMD  -> cmdQueue
        }
    }
}
