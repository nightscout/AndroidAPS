package info.nightscout.interfaces.stats

import android.content.Context
import android.util.LongSparseArray
import android.widget.TableLayout

interface TirCalculator {

    fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR>
    fun stats(context: Context): TableLayout
}