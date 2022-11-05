package info.nightscout.androidaps.interfaces.stats

import android.content.Context
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper

interface DexcomTIR {

    fun calculateSD(): Double
    fun toHbA1cView(context: Context, rh: ResourceHelper): TextView
    fun toSDView(context: Context, rh: ResourceHelper, profileFunction: ProfileFunction): TextView
    fun toRangeHeaderView(context: Context, rh: ResourceHelper, profileFunction: ProfileFunction): TextView
    fun toTableRowHeader(context: Context, rh: ResourceHelper): TableRow
    fun toTableRow(context: Context, rh: ResourceHelper): TableRow
}
