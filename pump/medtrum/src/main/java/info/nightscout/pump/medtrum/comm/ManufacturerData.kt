package info.nightscout.pump.medtrum.comm

import kotlin.experimental.and
import info.nightscout.pump.medtrum.extension.toLong

class ManufacturerData(manufacturerDataBytes: ByteArray) {
    private var deviceID: Long = 0
    private var deviceType = 0
    private var version = 0

    init {
        setData(manufacturerDataBytes)
    }

    private fun setData(inputData: ByteArray) {
        var index = 0
        val deviceIDBytes: ByteArray = inputData.copyOfRange(index, index + 4)
        deviceID = deviceIDBytes.toLong()
        index += 4
        deviceType = (inputData[index] and 0xff.toByte()).toInt()
        index += 1
        version = (inputData[index] and 0xff.toByte()).toInt()
    }

    fun getDeviceSN(): Long{
        return deviceID
    }

    fun getDeviceType(): Int {
        return deviceType
    }

    fun getVersion(): Int {
        return version
    }
}
