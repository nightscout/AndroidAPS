package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.toDrawable
import androidx.gridlayout.widget.GridLayout
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.keys.OverviewStringKey
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewMenusImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val config: Config,
    private val loop: Loop
) : OverviewMenus {

    enum class CharTypeData(
        @StringRes val nameId: Int,
        @AttrRes val attrId: Int,
        @AttrRes val attrTextId: Int,
        val primary: Boolean,
        val secondary: Boolean,
        @StringRes val shortnameId: Int,
        val enabledByDefault: Boolean = false,
        var visibility: () -> Boolean = { true }
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

    init {
        CharTypeData.PRE.visibility = {
            when {
                config.APS        -> loop.lastRun?.request?.hasPredictions == true
                config.AAPSCLIENT -> true
                else              -> false
            }
        }
        CharTypeData.DEVSLOPE.visibility = { config.isDev() }
        CharTypeData.VAR_SENS.visibility = { preferences.get(BooleanKey.ApsUseDynamicSensitivity) || (preferences.get(BooleanKey.ApsUseAutoIsfWeights) && config.isDev()) }
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
        get() = field.also {
            while (it.size < MAX_GRAPHS)
                it.add(Array(CharTypeData.entries.size) { false })
        }

    override val setting: List<Array<Boolean>>
        @Synchronized get() =
            if (!preferences.simpleMode) // implicitly does a list copy and update according to number of graphs
                _setting.toMutableList().also {
                    for (i in it.size - 1 downTo 1) {
                        if (!it[i].any { it })
                            it.removeAt(i)
                    }
                }
            else
                listOf(
                    arrayOf(true, true, true, false, false, false, false, false, false, false, false, false, false, false),
                    arrayOf(false, false, false, false, true, false, false, false, false, false, false, false, false, false),
                    arrayOf(false, false, false, false, false, true, false, false, false, false, false, false, false, false)
                )

    @Synchronized
    private fun storeGraphConfig() {
        val sts = Gson().toJson(_setting)
        preferences.put(OverviewStringKey.GraphConfig, sts)
        aapsLogger.debug(sts)
    }

    @Synchronized
    override fun loadGraphConfig() {
        assert(CharTypeData.entries.size == OverviewMenus.CharType.entries.size)
        val sts = preferences.get(OverviewStringKey.GraphConfig)
        if (sts.isNotEmpty()) {
            _setting = Gson().fromJson(sts, Array<Array<Boolean>>::class.java).toMutableList()
            // reset when new CharType added
            for (s in _setting)
                if (s.size != OverviewMenus.CharType.entries.size) {
                    _setting = ArrayList<Array<Boolean>>().also { it.add(Array(OverviewMenus.CharType.entries.size) { CharTypeData.entries[it].enabledByDefault }) }
                }
        } else {
            _setting = ArrayList<Array<Boolean>>().also { it.add(Array(OverviewMenus.CharType.entries.size) { CharTypeData.entries[it].enabledByDefault }) }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun setupChartMenu(chartButton: ImageButton, scaleButton: Button) {
        chartButton.setColorFilter(rh.gac(chartButton.context, app.aaps.core.ui.R.attr.defaultTextColor))
        scaleButton.setCompoundDrawablesWithIntrinsicBounds(
            null,
            null,
            rh.gd(R.drawable.ic_arrow_drop_down_white_24dp)?.also { it.setTint(rh.gac(scaleButton.context, app.aaps.core.ui.R.attr.defaultTextColor)) },
            null
        )
        chartButton.setOnClickListener { v: View ->
            var itemRow = 0
            val popup = PopupWindow(v.context)
            popup.setBackgroundDrawable(rh.gac(chartButton.context, app.aaps.core.ui.R.attr.popupWindowBackground).toDrawable())
            val scrollView = ScrollView(v.context)                        // required to be able to scroll menu on low res screen
            val horizontalScrollView = HorizontalScrollView(v.context)    // Workaround because I was not able to manage first column width for long labels
            horizontalScrollView.addView(scrollView)

            val layout = GridLayout(v.context)
            layout.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)  // not sure it works

            scrollView.addView(layout)
            layout.columnCount = MAX_GRAPHS

            // insert primary items
            CharTypeData.entries.forEach { m ->
                if (m.visibility.invoke() && m.primary) {
                    createCustomMenuItemView(v.context, m, itemRow, layout, true)
                    itemRow++
                }
            }

            // insert header row
            var layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(itemRow, 1), GridLayout.spec(0, 1))
            val textView = TextView(v.context).also {
                it.text = " ${rh.gs(R.string.graph_menu_divider_header)}"
                it.maxLines = 3                                                     // don't works currently
            }
            layout.addView(textView, layoutParamsLabel)
            for (i in 1..(MAX_GRAPHS - 1)) {
                val item = TextView(v.context).also {
                    it.gravity = Gravity.CENTER
                    it.text = "$i"
                }
                layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(itemRow, 1), GridLayout.spec(i, 1)).apply {
                    setGravity(Gravity.CENTER)
                }
                layout.addView(item, layoutParamsLabel)
            }
            itemRow++

            // insert secondary items
            CharTypeData.entries.forEach { m ->
                if (m.visibility.invoke() && m.secondary) {
                    createCustomMenuItemView(v.context, m, itemRow, layout, false)
                    itemRow++
                }
            }
            popup.contentView = horizontalScrollView
            // Permettre la fermeture de la PopupWindow en touchant en dehors
            popup.isOutsideTouchable = true
            popup.isFocusable = true
            popup.setOnDismissListener {    // remove empty graphs
                chartButton.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp)
                _setting.let {
                    for (i in it.size - 1 downTo 1) {
                        if (!isSecondary(it[i]))
                            it.removeAt(i)
                    }
                }
                storeGraphConfig()
                rxBus.send(EventRefreshOverview("OnMenuItemClickListener", now = true))
            }
            chartButton.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp)
            popup.showAsDropDown(v)
        }

        scaleButton.setOnClickListener { v: View ->
            val popup = PopupMenu(v.context, v)
            popup.menu.add(Menu.NONE, 6, Menu.NONE, rh.gs(R.string.graph_long_scale_6h))
            popup.menu.add(Menu.NONE, 12, Menu.NONE, rh.gs(R.string.graph_long_scale_12h))
            popup.menu.add(Menu.NONE, 18, Menu.NONE, rh.gs(R.string.graph_long_scale_18h))
            popup.menu.add(Menu.NONE, 24, Menu.NONE, rh.gs(R.string.graph_long_scale_24h))
            popup.setOnMenuItemClickListener {
                // id == Range to display ...
                rxBus.send(EventScale(it.itemId))
                return@setOnMenuItemClickListener true
            }
            scaleButton.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                rh.gd(R.drawable.ic_arrow_drop_up_white_24dp)?.also { it.setTint(rh.gac(v.context, app.aaps.core.ui.R.attr.defaultTextColor)) },
                null
            )
            popup.setOnDismissListener {
                scaleButton.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    rh.gd(R.drawable.ic_arrow_drop_down_white_24dp)?.also { it.setTint(rh.gac(v.context, app.aaps.core.ui.R.attr.defaultTextColor)) },
                    null
                )
            }
            popup.show()
            false
        }
    }

    fun isSecondary(graphArray: Array<Boolean>): Boolean = graphArray.filterIndexed { index, _ -> CharTypeData.entries[index].secondary }.reduce { acc, b -> acc || b }

    private fun createCustomMenuItemView(context: Context, m: CharTypeData, rowIndex: Int, layout: GridLayout, primary: Boolean) {
        var layoutParamsLabel = GridLayout.LayoutParams(GridLayout.spec(rowIndex, 1), GridLayout.spec(0, 1))
        val textView = TextView(context).also {
            it.text = formatedText(m)
            it.maxLines = 3                                                     // don't works currently
        }
        layout.addView(textView, layoutParamsLabel)

        val checkBoxes = mutableListOf<CheckBox>()

        // Create one or 4 checkbox
        for (i in 1..(MAX_GRAPHS - 1)) {
            val item = if (!primary || i == (MAX_GRAPHS - 1)) CheckBox(context) else TextView(context)
            item.id = i
            if (item is CheckBox) {
                if (primary)
                    item.isChecked = _setting[0][m.ordinal]
                else
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
                        checkBox.isChecked = false
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
            checkBox.setOnCheckedChangeListener(checkBoxListener)
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

    override fun scaleString(rangeToDisplay: Int): String = when (rangeToDisplay) {
        6    -> rh.gs(R.string.graph_scale_6h)
        12   -> rh.gs(R.string.graph_scale_12h)
        18   -> rh.gs(R.string.graph_scale_18h)
        24   -> rh.gs(R.string.graph_scale_24h)
        else -> ""
    }

}
