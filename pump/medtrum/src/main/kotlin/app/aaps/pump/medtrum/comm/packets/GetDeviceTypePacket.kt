package app.aaps.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.comm.enums.CommandType.GET_DEVICE_TYPE
import app.aaps.pump.medtrum.extension.toInt
import app.aaps.pump.medtrum.extension.toLong

class GetDeviceTypePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    var deviceType: Int = 0
    var deviceSN: Long = 0

    companion object {

        private const val RESP_DEVICE_TYPE_START = 6
        private const val RESP_DEVICE_TYPE_END = RESP_DEVICE_TYPE_START + 1
        private const val RESP_DEVICE_SN_START = 7
        private const val RESP_DEVICE_SN_END = RESP_DEVICE_SN_START + 4
    }

    init {
        opCode = GET_DEVICE_TYPE.code
        expectedMinRespLength = RESP_DEVICE_SN_END
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            deviceType = data.copyOfRange(RESP_DEVICE_TYPE_START, RESP_DEVICE_TYPE_END).toInt()
            deviceSN = data.copyOfRange(RESP_DEVICE_SN_START, RESP_DEVICE_SN_END).toLong()
        }

        return success
    }
}
