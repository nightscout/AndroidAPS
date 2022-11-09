package info.nightscout.androidaps.interfaces.stats

import android.content.Context
import android.util.LongSparseArray
import android.widget.TableLayout
import info.nightscout.androidaps.database.entities.TotalDailyDose

interface TddCalculator {

    fun calculate(days: Long): LongSparseArray<TotalDailyDose>
    fun calculateToday(): TotalDailyDose
    fun calculateDaily(startHours: Long, endHours: Long): TotalDailyDose
    fun calculate(startTime: Long, endTime: Long): TotalDailyDose
    fun averageTDD(tdds: LongSparseArray<TotalDailyDose>): TotalDailyDose?
    fun stats(context: Context): TableLayout
}
