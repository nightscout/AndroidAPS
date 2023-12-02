package app.aaps.core.interfaces.stats

import android.content.Context
import android.util.LongSparseArray
import android.widget.TableLayout
import app.aaps.core.data.model.TDD

interface TddCalculator {

    fun calculate(days: Long, allowMissingDays: Boolean): LongSparseArray<TDD>?
    fun calculateToday(): TDD?
    fun calculateDaily(startHours: Long, endHours: Long): TDD?
    fun calculate(startTime: Long, endTime: Long, allowMissingData: Boolean): TDD?
    fun averageTDD(tdds: LongSparseArray<TDD>?): TDD?
    fun stats(context: Context): TableLayout
}
