package info.nightscout.androidaps.plugins.general.overview

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.View
import android.widget.ImageButton
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import com.google.gson.Gson
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewMenus @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val buildHelper: BuildHelper,
    private val loopPlugin: LoopPlugin,
    private val config: Config
) {

    enum class CharType(@StringRes val nameId: Int, @ColorRes val colorId: Int, val primary: Boolean, val secondary: Boolean, @StringRes val shortnameId: Int) {
        PRE(R.string.overview_show_predictions, R.color.prediction, primary = true, secondary = false, shortnameId = R.string.prediction_shortname),
        BAS(R.string.overview_show_basals, R.color.basal, primary = true, secondary = false,shortnameId = R.string.basal_shortname),
        ABS(R.string.overview_show_absinsulin, R.color.iob, primary = false, secondary = true,shortnameId = R.string.abs_insulin_shortname),
        IOB(R.string.overview_show_iob, R.color.iob, primary = false, secondary = true,shortnameId = R.string.iob),
        COB(R.string.overview_show_cob, R.color.cob, primary = false, secondary = true,shortnameId = R.string.cob),
        DEV(R.string.overview_show_deviations, R.color.deviations, primary = false, secondary = true,shortnameId = R.string.deviation_shortname),
        SEN(R.string.overview_show_sensitivity, R.color.ratio, primary = false, secondary = true,shortnameId = R.string.sensitivity_shortname),
        ACT(R.string.overview_show_activity, R.color.activity, primary = true, secondary = true,shortnameId = R.string.activity_shortname),
        DEVSLOPE(R.string.overview_show_deviationslope, R.color.devslopepos, primary = false, secondary = true,shortnameId = R.string.devslope_shortname)
    }

    companion object {
        const val MAX_GRAPHS = 5 // including main
    }

    fun enabledTypes(graph: Int): String {
        val r = StringBuilder()
        for (type in CharType.values()) if (_setting[graph][type.ordinal]) {
            r.append(resourceHelper.gs(type.shortnameId))
            r.append(" ")
        }
        return r.toString()
    }



    private var _setting: MutableList<Array<Boolean>> = ArrayList()

    val setting: List<Array<Boolean>>
     get() = _setting.toMutableList() // implicitly does a list copy

    private fun storeGraphConfig() {
        val sts = Gson().toJson(_setting)
        sp.putString(R.string.key_graphconfig, sts)
        aapsLogger.debug(sts)
    }

    private fun loadGraphConfig() {
        val sts = sp.getString(R.string.key_graphconfig, "")
        if (sts.isNotEmpty()) {
            _setting = Gson().fromJson(sts, Array<Array<Boolean>>::class.java).toMutableList()
            // reset when new CharType added
            for (s in _setting)
                if (s.size != CharType.values().size) {
                    _setting = ArrayList()
                    _setting.add(Array(CharType.values().size) { true })
                }
        } else {
            _setting = ArrayList()
            _setting.add(Array(CharType.values().size) { true })
        }
    }

    fun setupChartMenu(chartButton: ImageButton) {
        loadGraphConfig()
        val settingsCopy = setting
        val numOfGraphs = settingsCopy.size // 1 main + x secondary

        chartButton.setOnClickListener { v: View ->
            val predictionsAvailable: Boolean = when {
                config.APS      -> loopPlugin.lastRun?.request?.hasPredictions ?: false
                config.NSCLIENT -> true
                else            -> false
            }
            val popup = PopupMenu(v.context, v)

            for (g in 0 until numOfGraphs) {
                if (g != 0 && g < numOfGraphs) {
                    val dividerItem = popup.menu.add(Menu.NONE, g, Menu.NONE, "------- ${resourceHelper.gs(R.string.graph_menu_divider_header)} $g -------")
                    dividerItem.isCheckable = true
                    dividerItem.isChecked = true
                }
                CharType.values().forEach { m ->
                    if (g == 0 && !m.primary) return@forEach
                    if (g > 0 && !m.secondary) return@forEach
                    var insert = true
                    if (m == CharType.PRE) insert = predictionsAvailable
                    if (m == CharType.DEVSLOPE) insert = buildHelper.isDev()
                    if (insert) {
                        val item = popup.menu.add(Menu.NONE, m.ordinal + 100 * (g + 1), Menu.NONE, resourceHelper.gs(m.nameId))
                        val title = item.title
                        val s = SpannableString(title)
                        s.setSpan(ForegroundColorSpan(resourceHelper.gc(m.colorId)), 0, s.length, 0)
                        item.title = s
                        item.isCheckable = true
                        item.isChecked = settingsCopy[g][m.ordinal]
                    }
                }
            }
            if (numOfGraphs < MAX_GRAPHS) {
                val dividerItem = popup.menu.add(Menu.NONE, numOfGraphs, Menu.NONE, "------- ${resourceHelper.gs(R.string.graph_menu_divider_header)} $numOfGraphs -------")
                dividerItem.isCheckable = true
                dividerItem.isChecked = false
            }

            popup.setOnMenuItemClickListener {
                // id < 100 graph header - divider 1, 2, 3 .....
                if (it.itemId == numOfGraphs) {
                    // add new empty
                    _setting.add(Array(CharType.values().size) { false })
                } else if (it.itemId < 100) {
                    // remove graph
                    _setting.removeAt(it.itemId)
                } else {
                    val graphNumber = it.itemId / 100 - 1
                    val item = it.itemId % 100
                    _setting[graphNumber][item] = !it.isChecked
                }
                storeGraphConfig()
                setupChartMenu(chartButton)
                rxBus.send(EventRefreshOverview("OnMenuItemClickListener", now = true))
                return@setOnMenuItemClickListener true
            }
            chartButton.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp)
            popup.setOnDismissListener { chartButton.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp) }
            popup.show()
        }
    }

}