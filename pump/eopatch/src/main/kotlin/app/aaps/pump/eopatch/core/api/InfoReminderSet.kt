package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseBooleanAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single

class InfoReminderSet : BaseBooleanAPI(PatchFunc.SET_INFO_REMINDER) {
    fun set(isInfoReminder: Boolean): Single<PatchBooleanResponse> =
        writeAndRead(allocate().putBoolean(isInfoReminder).putBoolean(false).build())
}
