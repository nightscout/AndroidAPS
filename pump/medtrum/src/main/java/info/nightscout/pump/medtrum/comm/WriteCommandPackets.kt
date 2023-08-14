package info.nightscout.pump.medtrum.comm

class WriteCommandPackets(data: ByteArray, sequenceNumber: Int) {

    private val CRC_8_TABLE: IntArray = intArrayOf(0, 155, 173, 54, 193, 90, 108, 247, 25, 130, 180, 47, 216, 67, 117, 238, 50, 169, 159, 4, 243, 104, 94, 197, 43, 176, 134, 29, 234, 113, 71, 220, 100, 255, 201, 82, 165, 62, 8, 147, 125, 230, 208, 75, 188, 39, 17, 138, 86, 205, 251, 96, 151, 12, 58, 161, 79, 212, 226, 121, 142, 21, 35, 184, 200, 83, 101, 254, 9, 146, 164, 63, 209, 74, 124, 231, 16, 139, 189, 38, 250, 97, 87, 204, 59, 160, 150, 13, 227, 120, 78, 213, 34, 185, 143, 20, 172, 55, 1, 154, 109, 246, 192, 91, 181, 46, 24, 131, 116, 239, 217, 66, 158, 5, 51, 168, 95, 196, 242, 105, 135, 28, 42, 177, 70, 221, 235, 112, 11, 144, 166, 61, 202, 81, 103, 252, 18, 137, 191, 36, 211, 72, 126, 229, 57, 162, 148, 15, 248, 99, 85, 206, 32, 187, 141, 22, 225, 122, 76, 215, 111, 244, 194, 89, 174, 53, 3, 152, 118, 237, 219, 64, 183, 44, 26, 129, 93, 198, 240, 107, 156, 7, 49, 170, 68, 223, 233, 114, 133, 30, 40, 179, 195, 88, 110, 245, 2, 153, 175, 52, 218, 65, 119, 236, 27, 128, 182, 45, 241, 106, 92, 199, 48, 171, 157, 6, 232, 115, 69, 222, 41, 178, 132, 31, 167, 60, 10, 145, 102, 253, 203, 80, 190, 37, 19, 136, 127, 228, 210, 73, 149, 14, 56, 163, 84, 207, 249, 98, 140, 23, 33, 186, 77, 214, 224, 123)

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
        val totalCommand: ByteArray = tmp + calcCrc8(tmp, tmp.size).toByte()

        if ((totalCommand.size - header.size) <= 15) {
            packages.add(totalCommand + 0.toByte())
        } else {
            var pkgIndex = 1
            var remainingCommand = totalCommand.copyOfRange(4, totalCommand.size)

            while (remainingCommand.size > 15) {
                header[3] = pkgIndex.toByte()
                tmp = header + remainingCommand.copyOfRange(0, 15)
                packages.add(tmp + calcCrc8(tmp, tmp.size).toByte())

                remainingCommand = remainingCommand.copyOfRange(15, remainingCommand.size)
                pkgIndex = (pkgIndex + 1) % 256
            }

            // Add last package
            header[3] = pkgIndex.toByte()
            tmp = header + remainingCommand
            packages.add(tmp + calcCrc8(tmp, tmp.size).toByte())
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

    private fun calcCrc8(value: ByteArray, size: Int): Int {
        var crc8 = 0
        for (i in 0 until size) {
            crc8 = CRC_8_TABLE[(value[i].toInt() and 255) xor (crc8 and 255)] and 255
        }
        return crc8
    }
}
