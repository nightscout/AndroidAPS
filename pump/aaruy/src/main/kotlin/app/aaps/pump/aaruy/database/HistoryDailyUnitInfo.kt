package app.aaps.pump.aaruy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_daily_unit_info")
class HistoryDailyUnitInfo {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
    var dateTimestamp: Long = 0 //时间戳，单位(毫秒)
    var dailyTotal: Int = 0 //每日总的注射量（乘以10），如102->10.2u
    var dailyBolus: Int = 0 //每日大剂量（乘以10），如102->10.2u
    var pumpName: String = ""
    var isReplace: Boolean = false
    var isUploaded: Boolean = false//是否已上传
    var timestamp:Long = 0
    var pumpType:Int = 0
    override fun toString(): String {
        return "HistoryDailyUnitInfo(id=$id, date=$dateTimestamp, dailyTotal=$dailyTotal, dailyBolus=$dailyBolus, pumpName='$pumpName', isReplace=$isReplace, " +
            "isUploaded=$isUploaded, timestamp=$timestamp, pumpType=$pumpType)"
    }


}