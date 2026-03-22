package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.BytesConverter
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.FirmwareVersionResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class GetFirmwareVersion @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<FirmwareVersionResponse>(PatchFunc.GET_FIRMWARE_VERSION, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): FirmwareVersionResponse {
        val success = bytes[DATA0].toInt() == 0
        if (!success) return FirmwareVersionResponse(false, 0, 0, 0, 0)
        return FirmwareVersionResponse(
            true,
            BytesConverter.toUInt(bytes[DATA1]), BytesConverter.toUInt(bytes[DATA2]),
            BytesConverter.toUInt(bytes[DATA3]), BytesConverter.toUInt(bytes[DATA6])
        )
    }

    fun get(): Single<FirmwareVersionResponse> = get(0x00)
    fun get(areaCode: Int): Single<FirmwareVersionResponse> = writeAndRead(allocate().putByte(RESERVED).putByte(areaCode).build())
}
