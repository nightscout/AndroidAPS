package info.nightscout.interfaces.stats

import android.content.Context
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.interfaces.profile.ProfileFunction

interface DexcomTIR {

    fun calculateSD(): Double
    fun toHbA1cView(context: Context): TextView
    fun toSDView(context: Context, profileFunction: ProfileFunction): TextView
    fun toRangeHeaderView(context: Context, profileFunction: ProfileFunction): TextView
    fun toTableRowHeader(context: Context): TableRow
    fun toTableRow(context: Context): TableRow
}
