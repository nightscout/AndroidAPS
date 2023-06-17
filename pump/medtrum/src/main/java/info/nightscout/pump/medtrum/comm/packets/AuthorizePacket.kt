package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.CommandType.AUTH_REQ
import info.nightscout.pump.medtrum.encryption.Crypt
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class AuthorizePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    companion object {

        private const val RESP_DEVICE_TYPE_START = 7
        private const val RESP_DEVICE_TYPE_END = RESP_DEVICE_TYPE_START + 1
        private const val RESP_VERSION_X_START = 8
        private const val RESP_VERSION_X_END = RESP_VERSION_X_START + 1
        private const val RESP_VERSION_Y_START = 9
        private const val RESP_VERSION_Y_END = RESP_VERSION_Y_START + 1
        private const val RESP_VERSION_Z_START = 10
        private const val RESP_VERSION_Z_END = RESP_VERSION_Z_START + 1
    }

    init {
        opCode = AUTH_REQ.code
        expectedMinRespLength = RESP_VERSION_Z_END
    }

    override fun getRequest(): ByteArray {
        val role = 2 // Fixed to 2 for pump
        val key = Crypt().keyGen(medtrumPump.pumpSN)
        return byteArrayOf(opCode) + byteArrayOf(role.toByte()) + medtrumPump.patchSessionToken.toByteArray(4) + key.toByteArray(4)
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            val deviceType = data.copyOfRange(RESP_DEVICE_TYPE_START, RESP_DEVICE_TYPE_END).toInt()
            val swVersion = "" + data.copyOfRange(RESP_VERSION_X_START, RESP_VERSION_X_END).toInt() + "." + data.copyOfRange(RESP_VERSION_Y_START, RESP_VERSION_Y_END).toInt() + "." + data.copyOfRange(
                RESP_VERSION_Z_START, RESP_VERSION_Z_END
            ).toInt()

            if (medtrumPump.deviceType != deviceType) {
                medtrumPump.deviceType = deviceType
            }
            if (medtrumPump.swVersion != swVersion) {
                medtrumPump.swVersion = swVersion
            }
            aapsLogger.debug(LTag.PUMPCOMM, "GetDeviceTypeState: deviceType: ${deviceType}, swVersion: ${swVersion}")
        }
        return success
    }
}
