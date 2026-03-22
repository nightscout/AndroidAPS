package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.scan.IBleDevice
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

@Singleton
class InfoReminderSet @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseBooleanAPI(PatchFunc.SET_INFO_REMINDER, patch, aapsLogger) {
    fun set(isInfoReminder: Boolean): Single<PatchBooleanResponse> =
        writeAndRead(allocate().putBoolean(isInfoReminder).putBoolean(false).build())
}
