package info.nightscout.androidaps.plugins.pump.eopatch.ble

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.ScanList
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState
import io.reactivex.rxjava3.core.Single

interface IPatchManager {

    fun init()
    fun updatePatchState(state: PatchState)
    fun setConnection()
    fun patchActivation(timeout: Long): Single<Boolean>
    fun scan(timeout: Long): Single<ScanList>
    fun addBolusToHistory(originalDetailedBolusInfo: DetailedBolusInfo)
    fun changeBuzzerSetting()
    fun changeReminderSetting()
    fun checkActivationProcess()
}
