package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.BatteryVoltageLevelPairingResponse
import io.reactivex.rxjava3.core.Single

class GetVoltageLevelB4Priming : BaseAPI<BatteryVoltageLevelPairingResponse>(PatchFunc.GET_VOLTAGE_B4_PRIMING) {
    override fun parse(bytes: ByteArray) = BatteryVoltageLevelPairingResponse(
        BytesConverter.toUInt(bytes[DATA0], bytes[DATA1]),
        BytesConverter.toUInt(bytes[DATA2]),
        bytes[DATA4].toInt()
    )

    fun get(): Single<BatteryVoltageLevelPairingResponse> = writeAndRead(generate())
}
