package app.aaps.pump.medtrum.comm

import app.aaps.pump.medtrum.util.CrcUtils.calcCrc8

class ReadDataPacket(data: ByteArray) {

    private var totalData = data.copyOfRange(0, data.size - 1) // Strip crc
    private var failed = false
    private var dataSize: Byte = data[0]
    private var sequenceNumber: Byte = data[3]

    init {
        val crcInitialChunk = calcCrc8(data.copyOfRange(0, data.size - 1), data.size - 1)

        if (crcInitialChunk != data[data.size - 1]) {
            failed = true
        }
    }

    fun addData(newData: ByteArray) {
        totalData += newData.copyOfRange(4, newData.size - 1) // Strip header and crc
        sequenceNumber++
        val crcNewChunk = calcCrc8(newData.copyOfRange(0, newData.size - 1), newData.size - 1)
        if (crcNewChunk != newData[newData.size - 1]) {
            failed = true
        }
        if (sequenceNumber != newData[3]) {
            failed = true
        }
    }

    fun allDataReceived(): Boolean {
        return (totalData.size >= dataSize)
    }

    fun getData(): ByteArray {
        return totalData
    }

    fun failed(): Boolean {
        return failed
    }
}
