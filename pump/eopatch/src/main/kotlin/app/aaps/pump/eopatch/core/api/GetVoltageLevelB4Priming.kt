package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.BatteryVoltageLevelPairingResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetVoltageLevelB4Priming @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BatteryVoltageLevelPairingResponse>(PatchFunc.GET_VOLTAGE_B4_PRIMING, patch, aapsLogger) {
    override fun parse(bytes: ByteArray) = BatteryVoltageLevelPairingResponse(
        BytesConverter.toUInt(bytes[DATA0], bytes[DATA1]),
        BytesConverter.toUInt(bytes[DATA2]),
        bytes[DATA4].toInt()
    )

    fun get(): Single<BatteryVoltageLevelPairingResponse> = writeAndRead(generate())
}
