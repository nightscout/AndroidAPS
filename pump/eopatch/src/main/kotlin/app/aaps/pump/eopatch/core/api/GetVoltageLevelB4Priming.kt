package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.BatteryVoltageLevelPairingResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import io.reactivex.rxjava3.core.Single
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class GetVoltageLevelB4Priming(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<BatteryVoltageLevelPairingResponse>(PatchFunc.GET_VOLTAGE_B4_PRIMING, patch, aapsLogger) {
    override fun parse(bytes: ByteArray) = BatteryVoltageLevelPairingResponse(
        BytesConverter.toUInt(bytes[DATA0], bytes[DATA1]),
        BytesConverter.toUInt(bytes[DATA2]),
        bytes[DATA4].toInt()
    )

    fun get(): Single<BatteryVoltageLevelPairingResponse> = writeAndRead(generate())
}
