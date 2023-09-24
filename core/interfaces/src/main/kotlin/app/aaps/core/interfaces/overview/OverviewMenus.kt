package app.aaps.core.interfaces.overview

import android.content.Context
import android.widget.ImageButton

interface OverviewMenus {
    enum class CharType {
        PRE,
        TREAT,
        BAS,
        ABS,
        IOB,
        COB,
        DEV,
        BGI,
        SEN,
        ACT,
        DEVSLOPE,
        HR
    }

    val setting: List<Array<Boolean>>
    fun loadGraphConfig()
    fun setupChartMenu(context: Context, chartButton: ImageButton)
    fun enabledTypes(graph: Int): String
    fun isEnabledIn(type: CharType): Int
}
