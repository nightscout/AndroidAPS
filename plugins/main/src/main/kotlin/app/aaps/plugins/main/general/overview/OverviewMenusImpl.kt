package app.aaps.plugins.main.general.overview

import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.gridlayout.widget.GridLayout
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.Preferences
import app.aaps.plugins.main.R
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewMenusImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val config: Config,
    private val loop: Loop,
    private val fabricPrivacy: FabricPrivacy
) : OverviewMenus {

    enum class CharTypeData(
        @StringRes val nameId: Int,
        @AttrRes val attrId: Int,
        @AttrRes val attrTextId: Int,
        val primary: Boolean,
        val secondary: Boolean,
        @StringRes val shortnameId: Int,
        val enabledByDefault: Boolean = false
    ) {

        PRE(R.string.overview_show_predictions, app.aaps.core.ui.R.attr.predictionColor, app.aaps.core.ui.R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.prediction_shortname, enabledByDefault = true),
        TREAT(R.string.overview_show_treatments, app.aaps.core.ui.R.attr.cobColor, app.aaps.core.ui.R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.treatments_shortname, enabledByDefault = true),
        BAS(R.string.overview_show_basals, app.aaps.core.ui.R.attr.basal, app.aaps.core.ui.R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.basal_shortname, enabledByDefault = true),
        ABS(R.string.overview_show_abs_insulin, app.aaps.core.ui.R.attr.iobColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.abs_insulin_shortname),
        IOB(R.string.overview_show_iob, app.aaps.core.ui.R.attr.iobColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = app.aaps.core.ui.R.string.iob),
        COB(R.string.overview_show_cob, app.aaps.core.ui.R.attr.cobColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = app.aaps.core.ui.R.string.cob),
        DEV(R.string.overview_show_deviations, app.aaps.core.ui.R.attr.bgiColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.deviation_shortname),
        BGI(R.string.overview_show_bgi, app.aaps.core.ui.R.attr.bgiColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.bgi_shortname),
        SEN(R.string.overview_show_sensitivity, app.aaps.core.ui.R.attr.ratioColor, app.aaps.core.ui.R.attr.menuTextColorInverse, primary = false, secondary = true, shortnameId = R.string.sensitivity_shortname),
        VAR_SENS(R.string.overview_show_variable_sens, app.aaps.core.ui.R.attr.ratioColor, app.aaps.core.ui.R.attr.menuTextColorInverse, primary = false, secondary = true, shortnameId = R.string.variable_sensitivity_shortname),
        ACT(R.string.overview_show_activity, app.aaps.core.ui.R.attr.activityColor, app.aaps.core.ui.R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.activity_shortname),
        DEVSLOPE(R.string.overview_show_deviation_slope, app.aaps.core.ui.R.attr.devSlopePosColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.devslope_shortname),
        HR(R.string.overview_show_heartRate, app.aaps.core.ui.R.attr.heartRateColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.heartRate_shortname),
        STEPS(R.string.overview_show_steps, app.aaps.core.ui.R.attr.stepsColor, app.aaps.core.ui.R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.steps_shortname),
    }

    companion object {

        const val MAX_GRAPHS = 5 // including main
    }

    override fun enabledTypes(graph: Int): String {
        val r = StringBuilder()
        for (type in CharTypeData.entries)
            if (setting[graph][type.ordinal]) {
                r.append(rh.gs(type.shortnameId))
                r.append(" ")
            }
        return r.toString()
    }

    private var _setting: MutableList<Array<Boolean>> = ArrayList()

    override val setting: List<Array<Boolean>>
        @Synchronized get() =
            if (!preferences.simpleMode) // implicitly does a list copy and update according to number of graphs
                _setting.toMutableList().also {
                    while(_setting.size < MAX_GRAPHS)
                        _setting.add(Array(CharTypeData.entries.size) { false })
                }
            else
                listOf(
                    arrayOf(true, true, true, false, false, false, false, false, false, false, false, false, false, false),
                    arrayOf(false, false, false, false, true, false, false, false, false, false, false, false, false, false),
                    arrayOf(false, false, false, false, false, true, false, false, false, false, false, false, false, false),
                    arrayOf(false, false, false, false, false, false, false, false, false, false, false, false, false, false),
                    arrayOf(false, false, false, false, false, false, false, false, false, false, false, false, false, false)
                )

    @Synchronized
    private fun storeGraphConfig() {
        val sts = Gson().toJson(_setting)
        sp.putString(R.string.key_graph_config, sts)
        aapsLogger.debug(sts)
    }

    @Synchronized
    override fun loadGraphConfig() {
        assert(CharTypeData.entries.size == OverviewMenus.CharType.entries.size)
        val sts = sp.getString(R.string.key_graph_config, "")
        if (sts.isNotEmpty()) {
            _setting = Gson().fromJson(sts, Array<Array<Boolean>>::class.java).toMutableList()
            // reset when new CharType added
            for (s in _setting)
                if (s.size != OverviewMenus.CharType.entries.size) {
                    _setting = ArrayList()
                    _setting.add(Array(OverviewMenus.CharType.entries.size) { CharTypeData.entries[it].enabledByDefault })
                }
        } else {
            _setting = ArrayList()
            _setting.add(Array(OverviewMenus.CharType.entries.size) { CharTypeData.entries[it].enabledByDefault })
        }
    }

    override fun setupChartMenu(context: Context, chartButton: ImageButton) {
        var itemRow = 0

        chartButton.setOnClickListener { v: View ->
            val predictionsAvailable: Boolean = when {
                config.APS      -> loop.lastRun?.request?.hasPredictions ?: false
                config.NSCLIENT -> true
                else            -> false
            }
            val popup = PopupWindow(v.context)
            val layout = GridLayout(v.context)
            layout.columnCount = 5

            // instert primary items
            CharTypeData.entries.forEach { m ->
                var insert = true
                if (m == CharTypeData.PRE) insert = predictionsAvailable
                if (insert && m.primary) {
                    createCustomMenuItemView(v.context, m, itemRow, layout)
                    itemRow++
                }
            }

            // insert hearder row
            var layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(itemRow, 1), GridLayout.spec(0, 1))
            val textView = TextView(context).also {
                it.text = " ${rh.gs(R.string.graph_menu_divider_header)}"
            }
            layout.addView(textView, layoutParamsLabel)
            for (i in 1..4) {
                val item = TextView(context).also {
                    it.gravity = Gravity.CENTER
                    it.text = "$i"
                }
                layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(itemRow, 1), GridLayout.spec(i, 1)).apply {
                    setGravity(Gravity.CENTER)
                }
                layout.addView(item, layoutParamsLabel)
            }
            itemRow++

            // instert secondary items
            CharTypeData.entries.forEach { m ->
                var insert = true
                if (m == CharTypeData.DEVSLOPE) insert = config.isDev()
                if (insert && m.secondary) {
                    createCustomMenuItemView(v.context, m, itemRow, layout)
                    itemRow++
                }
            }

            popup.contentView = layout
            // Permettre la fermeture de la PopupWindow en touchant en dehors
            popup.isOutsideTouchable = true
            popup.isFocusable = true

            popup.setOnDismissListener {
                // todo: reorganize graph to remove empty graphs in the middle

                rxBus.send(EventRefreshOverview("OnMenuItemClickListener", now = true))
            }

            popup.showAsDropDown(v)
        }
    }

    private fun createCustomMenuItemView(context: Context, m: CharTypeData, rowIndex: Int, layout: GridLayout)  {
        var layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(rowIndex, 1), GridLayout.spec(0, 1))
        val textView = TextView(context).also {
            it.text = formatedText(m)
        }
        layout.addView(textView, layoutParamsLabel)

        val checkBoxes = mutableListOf<CheckBox>()

        // Create one or 4 checkbox
        for (i in 1..4) {
            val item = if (m.secondary || i == 4) CheckBox(context) else TextView(context)
            item.id = i
            if (item is CheckBox) {
                if (m.primary)
                    item.isChecked = _setting[0][m.ordinal]
                if (m.secondary)
                    item.isChecked = _setting[i][m.ordinal]
                checkBoxes.add(item)
            }
            layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(rowIndex, 1), GridLayout.spec(i, 1))
            layout.addView(item, layoutParamsLabel)
        }

        val checkBoxListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            // Update other Checkboxes on same row to allow a curve to only one subgraph
            if (isChecked) {
                checkBoxes.forEach { checkBox ->
                    if (checkBox != buttonView) {
                        if (checkBox is CheckBox) checkBox.isChecked = false
                    }
                }
            }
            if (m.primary)
                _setting[0][m.ordinal] = checkBoxes[0].isChecked
            else {
                checkBoxes.forEach { checkBox ->
                    _setting[checkBox.id][m.ordinal] = checkBox.isChecked
                }
            }
            storeGraphConfig()
            rxBus.send(EventRefreshOverview("OnMenuItemClickListener", now = true))
        }

        checkBoxes.forEach { checkBox ->
            if (checkBox is CheckBox) checkBox.setOnCheckedChangeListener(checkBoxListener)
        }
    }
    private fun formatedText(m: CharTypeData): SpannableString {
        return SpannableString(" ${rh.gs(m.nameId)} ").also {
            it.setSpan(ForegroundColorSpan(rh.gac(m.attrTextId)), 0, it.length, 0)
            it.setSpan(BackgroundColorSpan(rh.gac(m.attrId)), 0, it.length, 0)
        }
    }

    override fun isEnabledIn(type: OverviewMenus.CharType): Int {
        val settingsCopy = setting
        val numOfGraphs = settingsCopy.size // 1 main + x secondary
        for (g in 0 until numOfGraphs) if (settingsCopy[g][type.ordinal]) return g
        return -1
    }

}
