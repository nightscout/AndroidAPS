package app.aaps.core.interfaces.stats

import android.content.Context
import android.widget.TableLayout
import androidx.collection.LongSparseArray

interface TirCalculator {

    fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR>
    fun calculateToday(lowMgdl: Double, highMgdl: Double): TIR
    fun calculateRange(start: Long, end: Long, lowMgdl: Double, highMgdl: Double): TIR
    fun stats(context: Context): TableLayout
}