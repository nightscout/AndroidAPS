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
        VAR_SEN,
        ACT,
        DEVSLOPE,
        HR,
        STEPS
    }

    val setting: List<Array<Boolean>>
    fun loadGraphConfig()
    fun setupChartMenu(context: Context, chartButton: ImageButton)
    fun enabledTypes(graph: Int): String
    fun isEnabledIn(type: CharType): Int
}
