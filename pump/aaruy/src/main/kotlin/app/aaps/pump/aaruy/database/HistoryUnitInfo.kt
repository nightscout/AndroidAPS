package app.aaps.pump.aaruy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_unit_info")
class HistoryUnitInfo {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
    var time: Long = 0  // 毫秒

    //0:基础率 1：临时基础率 2：大剂量（延长量）3:报警  4:推杆回退  5：推杆定位 6：恢复出厂设置 7：暂停  108:进食碳水化合物  109:血糖值 110:换泵
    var type: Int = 0

    //当type为0时，该值是基础率值(U/小时);当type为1时，该值是临时基础率值(U/小时);
    // 当type为2时，该值是大剂量立即量值(U);
    var data: Long = 0

    //大剂量立即量注射时长，单位为秒
    var immTime: Long = 0

    //注射时间(分钟)，当为大剂量(即type==2)时，该值为0则没有延长量，若该值不为0，
    // 则delayBolus就为延长量注射剂量;当type = 8时，表示hour小时
    var injectTime: Long = 0

    //当为大剂量(即type==2)时，该值才有效，表示大剂量延长量值   当type = 108时，表示碳水化合物值
    var delayBolus: Long = 0

    //当type = 109时，表示血糖值
    var bgValue: Double = 0.0
    var reserved: Int = 0
    // bit0-1, 当type为2时，0:餐时大剂量 1:临时大剂量 2:微型大剂量
    var blueName: String = ""
    var isUploaded: Boolean = false//是否已上传
    var pumpType:Int = 0

    override fun toString(): String {
        return "HistoryUnitInfo(id=$id, time=$time, type=$type, data=$data, immTime=$immTime, injectTime=$injectTime, delayBolus=$delayBolus, " +
            "bgValue=$bgValue, reserved=$reserved, blueName='$blueName', isUploaded=$isUploaded, pumpType=$pumpType)"
    }


}