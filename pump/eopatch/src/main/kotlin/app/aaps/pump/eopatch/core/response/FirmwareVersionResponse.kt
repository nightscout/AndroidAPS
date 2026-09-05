package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import java.util.Locale

class FirmwareVersionResponse(
    success: Boolean,
    private val major: Int,
    private val minor1: Int,
    private val minor2: Int,
    private val minor3: Int
) : BaseResponse(if (success) PatchBleResultCode.SUCCESS else PatchBleResultCode.UNKNOWN_ERROR) {

    val firmwareVersionString: String?
        get() = if (!isSuccess) null else String.format(Locale.US, "%d.%d.%d.%d", major, minor1, minor2, minor3)
}
