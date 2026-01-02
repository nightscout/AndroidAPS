package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.CommonUtils
import app.aaps.pump.eopatch.FloatFormatters
import app.aaps.pump.eopatch.code.UnitOrPercent
import java.util.Locale
import java.util.concurrent.TimeUnit

class TempBasal {

    var startTimestamp = 0L
    var durationMinutes: Long = 0L
    var doseUnitPerHour: Float = -1f
    var percent: Int = 0
    var unitDefinition: UnitOrPercent? = null

    var running = false

    val endTimestamp: Long
        get() = if (this.startTimestamp == 0L) 0 else this.startTimestamp + TimeUnit.MINUTES.toMillis(this.durationMinutes)

    val doseUnitText: String
        get() = String.format("%s U/hr", FloatFormatters.insulin(doseUnitPerHour))

    val remainTimeText: String
        get() {
            var diff = endTimestamp - System.currentTimeMillis()
            if (diff < 0) diff = 0
            val remainTime = CommonUtils.getRemainHourMin(diff)
            return String.format(Locale.getDefault(), "%02d:%02d", remainTime.first, remainTime.second)
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
        this.running = false
    }

    override fun toString(): String {
        return "TempBasal(startTimestamp=$startTimestamp, durationMinutes=$durationMinutes, doseUnitPerHour=$doseUnitPerHour, percent=$percent)"
    }

    companion object {

        fun createAbsolute(durationMinutes: Long, doseUnitPerHour: Float): TempBasal {
            val b = TempBasal()
            b.durationMinutes = durationMinutes
            b.doseUnitPerHour = doseUnitPerHour
            b.unitDefinition = UnitOrPercent.U
            return b
        }

        fun createPercent(durationMinutes: Long, percent: Int): TempBasal {
            val b = TempBasal()
            b.durationMinutes = durationMinutes
            b.percent = percent
            b.unitDefinition = UnitOrPercent.P
            return b
        }
    }

}
