package app.aaps.core.interfaces.stats

import android.content.Context
import android.widget.TableLayout
import androidx.collection.LongSparseArray

interface TirCalculator {

    fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR>
    fun stats(context: Context): TableLayout
}