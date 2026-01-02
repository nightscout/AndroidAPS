package app.aaps.core.graph.data

import android.content.Context
import android.util.AttributeSet
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.Series

open class GraphViewWithCleanup @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : GraphView(context, attrs, defStyle) {

    override fun removeAllSeries() {
        series.forEach {
            it.setOnDataPointTapListener(null)
        }
        super.removeAllSeries()
    }

    override fun removeSeries(series: Series<*>?) {
        series?.setOnDataPointTapListener(null)
        super.removeSeries(series)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeAllSeries()
    }
}