package app.aaps.core.interfaces.stats

import android.content.Context
import android.util.LongSparseArray
import android.widget.TableLayout
import app.aaps.database.entities.TotalDailyDose

interface TddCalculator {

    fun calculate(days: Long, allowMissingDays: Boolean): LongSparseArray<TotalDailyDose>?
    fun calculateToday(): TotalDailyDose?
    fun calculateDaily(startHours: Long, endHours: Long): TotalDailyDose?
    fun calculate(startTime: Long, endTime: Long, allowMissingData: Boolean): TotalDailyDose?
    fun averageTDD(tdds: LongSparseArray<TotalDailyDose>?): TotalDailyDose?
    fun stats(context: Context): TableLayout
}
