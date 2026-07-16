package app.aaps.pump.aaruy.ui.viewmodel

import androidx.lifecycle.LiveData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyService
import app.aaps.pump.aaruy.ui.AaruyBaseNavigator
import app.aaps.pump.aaruy.ui.viewmodel.BaseViewModel
import javax.inject.Inject

class AaruyHistoryViewModel@Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aaruyPlugin: AaruyPlugin,
    private val commandQueue: CommandQueue,
    private val preferences: Preferences,
    private val aaruyHistoryDatabase: AaruyHistoryDatabase,
    val aaruyPump: AaruyPump
) : BaseViewModel<AaruyBaseNavigator>() {

    val aaruyService: AaruyService?
        get() = aaruyPlugin.getService()


    private var historyLiveDataList: LiveData<List<HistoryUnitInfo>>? = null
    private var historyDailyLiveDataList: LiveData<MutableList<HistoryDailyUnitInfo>>? = null

    init {
        historyLiveDataList = aaruyHistoryDatabase.historyUnitInfoDao().queryLiveDataList()
        historyDailyLiveDataList = aaruyHistoryDatabase.historyDailyUnitInfoDao().queryLiveDataList()
    }

    fun getAllHistoryLiveData(): LiveData<List<HistoryUnitInfo>> {
        historyLiveDataList = aaruyHistoryDatabase.historyUnitInfoDao().queryLiveDataList()
        return historyLiveDataList!!
    }

    fun getAllHistory(): List<HistoryUnitInfo> {
        return aaruyHistoryDatabase.historyUnitInfoDao().queryAll()
    }

    fun getAllHistoryLiveDataByType(type: Int): LiveData<List<HistoryUnitInfo>> {
        return aaruyHistoryDatabase.historyUnitInfoDao().queryAllLiveDataByType(type)
    }

    fun getAllDaily():List<HistoryDailyUnitInfo>{
        return aaruyHistoryDatabase.historyDailyUnitInfoDao().queryAll()
    }
    fun getAllHistoryDailyLiveData(): LiveData<MutableList<HistoryDailyUnitInfo>> {
        historyDailyLiveDataList = aaruyHistoryDatabase.historyDailyUnitInfoDao().queryLiveDataList()
        return historyDailyLiveDataList!!
    }

    fun readDailyData(tag: String, fromServer: UByte = 0u) {
        aaruyService?.readDailyData()
    }

    fun requestHistoryData(fromServer: UByte = 0u) {
        aaruyService?.requestHistoryData()
    }

    fun connectDevice() {
        if (preferences.get(AaruyStringKey.MacAddress) != "") {
            aaruyService?.connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
        }
    }
}