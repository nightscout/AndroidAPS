package info.nightscout.pump.medtrum.comm

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.encryption.Crypt
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

// TODO object would be better? Or split this class up in an entirely different way
@Singleton
class WriteCommand @Inject internal constructor(
    private val dateUtil: DateUtil
) {

    val COMMAND_SYNCHRONIZE: Byte = 3
    val COMMAND_SUBSCRIBE: Byte = 4
    val COMMAND_AUTH_REQ: Byte = 5
    val COMMAND_GET_DEVICE_TYPE: Byte = 6
    val COMMAND_SET_TIME: Byte = 10
    val COMMAND_GET_TIME: Byte = 11
    val COMMAND_SET_TIME_ZONE: Byte = 12

    private val mCrypt = Crypt()
    private val timeUtil = MedtrumTimeUtil()

    fun authorize(deviceSerial: Long): ByteArray {
        val role = 2 // Fixed to 2 for pump
        val key = mCrypt.keyGen(deviceSerial)
        return byteArrayOf(COMMAND_AUTH_REQ) + byteArrayOf(role.toByte()) + 0.toByteArray(4) + key.toByteArray(4)
    }

    fun getDeviceType(): ByteArray {
        return byteArrayOf(COMMAND_GET_DEVICE_TYPE)
    }

    fun getTime(): ByteArray {
        return byteArrayOf(COMMAND_GET_TIME)
    }

    fun setTime(): ByteArray {
        val time = timeUtil.getCurrentTimePumpSeconds()
        return byteArrayOf(COMMAND_SET_TIME) + 2.toByte() + time.toByteArray(4)
    }

    fun setTimeZone(): ByteArray {
        val time = timeUtil.getCurrentTimePumpSeconds()
        var offsetMins = dateUtil.getTimeZoneOffsetMinutes(dateUtil.now())
        if (offsetMins < 0) offsetMins += 65536
        return byteArrayOf(COMMAND_SET_TIME_ZONE) + offsetMins.toByteArray(2) + time.toByteArray(4)
    }

    fun synchronize(): ByteArray {
        return byteArrayOf(COMMAND_SYNCHRONIZE)
    }

    fun subscribe(): ByteArray {
        return byteArrayOf(COMMAND_SUBSCRIBE) + 4095.toByteArray(2)
    }
}
