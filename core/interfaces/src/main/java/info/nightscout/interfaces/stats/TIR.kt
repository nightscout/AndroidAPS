package info.nightscout.interfaces.stats

import android.content.Context
import android.widget.TableRow
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil

interface TIR {

    val date: Long
    val lowThreshold: Double
    val highThreshold: Double
    var below: Int
    var inRange: Int
    var above: Int
    var error: Int
    var count: Int
    fun error()
    fun below()
    fun inRange()
    fun above()

    fun toTableRow(context: Context, rh: ResourceHelper, dateUtil: DateUtil): TableRow
    fun toTableRow(context: Context, rh: ResourceHelper, days: Int): TableRow
}
