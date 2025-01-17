package app.aaps.core.graph.data

import com.jjoe64.graphview.DefaultLabelFormatter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Created by mike on 09.06.2016.
 */
class TimeAsXAxisLabelFormatter(private val format: String) : DefaultLabelFormatter() {

    override fun formatLabel(value: Double, isValueX: Boolean): String =
        if (isValueX) {
            // format as date
            val dateFormat: DateFormat = SimpleDateFormat(format, Locale.getDefault())
            dateFormat.format(value.toLong())
        } else {
            try {
                // unknown reason for crashing on this
                //                Fatal Exception: java.lang.NullPointerException
                //                Attempt to invoke virtual method 'double com.jjoe64.graphview.Viewport.getMaxY(boolean)' on a null object reference
                //                com.jjoe64.graphview.DefaultLabelFormatter.formatLabel (DefaultLabelFormatter.java:89)
                //                app.aaps.interfaces.graph.data.TimeAsXAxisLabelFormatter.formatLabel (TimeAsXAxisLabelFormatter.java:26)
                //                com.jjoe64.graphview.GridLabelRenderer.drawVerticalSteps (GridLabelRenderer.java:1057)
                //                com.jjoe64.graphview.GridLabelRenderer.draw (GridLabelRenderer.java:866)
                //                com.jjoe64.graphview.GraphView.onDraw (GraphView.java:296)
                super.formatLabel(value, false)
            } catch (ignored: Exception) {
                ""
            }
        }
}
