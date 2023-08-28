package info.nightscout.pump.medtrum.comm

class ReadDataPacket(data: ByteArray) {

    private var totalData = data.copyOfRange(0, data.size - 1) // Strip crc
    private var dataSize: Byte = data[0]

    fun addData(newData: ByteArray) {
        totalData += newData.copyOfRange(4, newData.size - 1) // Strip header and crc
    }

    fun allDataReceived(): Boolean {
        return (totalData.size >= dataSize)
    }

    fun getData(): ByteArray {
        return totalData
    }
}
