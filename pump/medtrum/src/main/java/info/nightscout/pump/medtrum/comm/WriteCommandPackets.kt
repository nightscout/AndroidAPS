package info.nightscout.pump.medtrum.comm

import info.nightscout.pump.medtrum.encryption.Crypt


class WriteCommandPackets(private val command: ByteArray) {

    val crypt = Crypt()

    private val packages = mutableListOf<ByteArray>()
    private var index = 0
    private var writeCommandIndex = 0
    private var allPacketsConsumed = false


    init {
        setData(command)
    }

    fun setData(inputData: ByteArray) {
        resetPackets()
        // PackageIndex: 0 initially, if there are multiple packet, for the first packet it is set to 0 (not included in crc calc but sent in actual header)
        var pkgIndex = 0
        var header = byteArrayOf(
            (inputData.size + 4).toByte(),
            inputData[0],
            writeCommandIndex.toByte(),
            pkgIndex.toByte()
        )

        var tmp: ByteArray = header + inputData.copyOfRange(1, inputData.size)
        var totalCommand: ByteArray = tmp + crypt.calcCrc8(tmp, tmp.size).toByte()

        if ((totalCommand.size - header.size) <= 15) {
            packages.add(totalCommand + 0.toByte())
        } else {
            pkgIndex = 1
            var remainingCommand = totalCommand.copyOfRange(4, totalCommand.size)

            while (remainingCommand.size > 15) {
                header[3] = pkgIndex.toByte()
                tmp = header + remainingCommand.copyOfRange(0, 15)
                packages.add(tmp + crypt.calcCrc8(tmp, tmp.size).toByte())

                remainingCommand = remainingCommand.copyOfRange(15, remainingCommand.size)
                pkgIndex = (pkgIndex + 1) % 256
            }

            // Add last package
            header[3] = pkgIndex.toByte()
            tmp = header + remainingCommand
            packages.add(tmp + crypt.calcCrc8(tmp, tmp.size).toByte())
        }
        writeCommandIndex = (writeCommandIndex % 255) + 1
    }


    fun allPacketsConsumed(): Boolean {
        return allPacketsConsumed
    }

    fun getNextPacket(): ByteArray? {
        var ret: ByteArray? = null
        if (index < packages.size) {
            ret = packages[index]
            index++
        }
        if (index >= packages.size) {
            allPacketsConsumed = true
        }
        return ret
    }

    private fun resetPackets() {
        packages.clear()
        index = 0
        allPacketsConsumed = false
    }
}
