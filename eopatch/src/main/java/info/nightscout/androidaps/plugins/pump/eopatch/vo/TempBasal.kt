package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.androidaps.plugins.pump.eopatch.CommonUtils
import info.nightscout.androidaps.plugins.pump.eopatch.FloatFormatters
import info.nightscout.androidaps.plugins.pump.eopatch.core.util.FloatAdjusters
import info.nightscout.androidaps.plugins.pump.eopatch.code.UnitOrPercent
import java.util.concurrent.TimeUnit

class TempBasal {
   var startTimestamp = 0L
   var durationMinutes: Long = 0L
   var doseUnitPerHour: Float = -1f
   var percent: Int = 0
   var unitDefinition: UnitOrPercent? = null // percenr or U
       set(_unitDefinition: UnitOrPercent?) {
           field = _unitDefinition
       }


   var running =false

    // val isGreaterThanMaxBasal: Boolean
    //     get() = isGreaterThan(25f)  //todo

    val percentUs: FloatArray
        get() {
            val doseUs: FloatArray

            doseUs = FloatArray((this.durationMinutes / 30).toInt())
            for (i in doseUs.indices) {
                doseUs[i] = this.percent.toFloat()
            }

            return doseUs
        }

    val doseUnitPerHourArray: FloatArray
        get() {
            val doseUs: FloatArray

            doseUs = FloatArray((this.durationMinutes / 30).toInt())

            val value = FloatAdjusters.ROUND2_INSULIN.apply(doseUnitPerHour)
            for (i in doseUs.indices) {
                doseUs[i] = value
            }
            return doseUs
        }

    val endTimestamp: Long
        get() = if (this.startTimestamp == 0L) 0 else this.startTimestamp + TimeUnit.MINUTES.toMillis(this.durationMinutes)


    val doseUnitText: String
        get() = String.format("%s U/hr", FloatFormatters.insulin(doseUnitPerHour))

    val remainTimeText: String
        get() {
            var diff = endTimestamp - System.currentTimeMillis()
            if (diff < 0) diff = 0
            val remainTime = CommonUtils.getRemainHourMin(diff)
            return String.format("%02d:%02d", remainTime.first, remainTime.second)
        }

    init {
        initObject()
    }

    fun initObject() {
        this.unitDefinition = UnitOrPercent.U
        this.doseUnitPerHour = 0f
        this.percent = 0
        this.durationMinutes = 0
        this.startTimestamp = 0
    }

    fun getDoseUnitPerHourWithPercent(doseUnitPerHour: Float): Float {
        return doseUnitPerHour * this.percent / 100f
    }

    fun isGreaterThan(maxBasal: Float, normalBasalManager: NormalBasalManager): Boolean {
        var maxTempBasal = 0f
        if (this.unitDefinition == UnitOrPercent.U) {
            maxTempBasal = this.doseUnitPerHour
        } else if (this.unitDefinition == UnitOrPercent.P) {
            val normalBasal = normalBasalManager.normalBasal
            if (normalBasal != null) {
                val maxNormalBasal = normalBasal.getMaxBasal(durationMinutes)
                maxTempBasal = FloatAdjusters.ROUND2_TEMP_BASAL_PROGRAM_RATE.apply(maxNormalBasal + getDoseUnitPerHourWithPercent(maxNormalBasal))
            }
        }
        return if (maxTempBasal > maxBasal) {
            true
        } else false
    }

    override fun toString(): String {
        return "TempBasal(startTimestamp=$startTimestamp, durationMinutes=$durationMinutes, doseUnitPerHour=$doseUnitPerHour, percent=$percent)"
    }

    companion object {
       fun createAbsolute( _durationMinutes: Long,  _doseUnitPerHour: Float): TempBasal {
           val b = TempBasal()
           b.durationMinutes = _durationMinutes
           b.doseUnitPerHour = _doseUnitPerHour
           b.unitDefinition = UnitOrPercent.U
           return b
       }
       fun createPercent( _durationMinutes: Long, _percent: Int): TempBasal {
           val b = TempBasal()
           b.durationMinutes = _durationMinutes
           b.percent = _percent
           b.unitDefinition = UnitOrPercent.P
           return b
       }
   }

}
