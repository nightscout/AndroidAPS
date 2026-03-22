package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.response.BondingResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class StartBonding @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BondingResponse>(PatchFunc.REQUEST_BONDING, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): BondingResponse {
        val ret = bytes[DATA0].toInt()
        val resultCode = if (ret == REQUEST_FAIL) PatchBleResultCode.UNKNOWN_ERROR else PatchBleResultCode.SUCCESS
        return BondingResponse(resultCode, ret)
    }

    fun start(option: Int): Single<BondingResponse> = writeAndRead(allocate().putByte(0).putByte(option).build())

    companion object {
        const val OPTION_PASSKEY = 0
        const val OPTION_NUMERIC = 1
        const val REQUEST_SUCCESS = 0
        const val REQUEST_FAIL = 1
        const val BONDED = 2
        const val BONDING = 3
    }
}
