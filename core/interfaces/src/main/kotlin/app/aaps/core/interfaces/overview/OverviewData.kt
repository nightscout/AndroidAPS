package app.aaps.core.interfaces.overview

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes

interface OverviewData {

    var rangeToDisplay: Int // for graph
    var toTime: Long  // current time rounded up to 1 hour
    var fromTime: Long // toTime - range
    var endTime: Long // toTime + predictions

    fun reset()
    fun initRange()

    var pumpStatus: String
    var calcProgressPct: Int

    fun temporaryBasalText(): String
    fun temporaryBasalDialogText(): String
    @DrawableRes fun temporaryBasalIcon(): Int
    @AttrRes fun temporaryBasalColor(context: Context?): Int

    fun extendedBolusText(): String
    fun extendedBolusDialogText(): String
}
