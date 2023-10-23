package info.nightscout.pump.medtrum.comm

import CrcUtils.calcCrc8

class WriteCommandPackets(data: ByteArray, sequenceNumber: Int) {

    private val packages = mutableListOf<ByteArray>()
    private var index = 0

    init {
        // PackageIndex: 0 initially, if there are multiple packets, for the first packet it is set to 0 (not included in CRC calculation but sent in actual header)
        val header = byteArrayOf(
            (data.size + 4).toByte(),
            data[0],
            sequenceNumber.toByte(),
            0.toByte() // pkgIndex
        )

        var tmp: ByteArray = header + data.copyOfRange(1, data.size)
        val totalCommand: ByteArray = tmp + calcCrc8(tmp, tmp.size)

        if ((totalCommand.size - header.size) <= 15) {
            packages.add(totalCommand + 0.toByte())
        } else {
            var pkgIndex = 1
            var remainingCommand = totalCommand.copyOfRange(4, totalCommand.size)

            while (remainingCommand.size > 15) {
                header[3] = pkgIndex.toByte()
                tmp = header + remainingCommand.copyOfRange(0, 15)
                packages.add(tmp + calcCrc8(tmp, tmp.size))

                remainingCommand = remainingCommand.copyOfRange(15, remainingCommand.size)
                pkgIndex = (pkgIndex + 1) % 256
            }

            // Add last package
            header[3] = pkgIndex.toByte()
            tmp = header + remainingCommand
            packages.add(tmp + calcCrc8(tmp, tmp.size))
        }
    }

    fun getNextPacket(): ByteArray? {
        var ret: ByteArray? = null
        if (index < packages.size) {
            ret = packages[index]
            index++
        }
        return ret
    }

    fun allPacketsConsumed(): Boolean {
        return index >= packages.size
    }
}
