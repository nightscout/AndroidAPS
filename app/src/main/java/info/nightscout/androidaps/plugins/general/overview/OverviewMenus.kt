package info.nightscout.androidaps.plugins.general.overview

import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.View
import android.widget.ImageButton
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import com.google.gson.Gson
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.events.EventScale
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewMenus @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val buildHelper: BuildHelper,
    private val loop: Loop,
    private val config: Config,
    private val fabricPrivacy: FabricPrivacy
) {

    enum class CharType(@StringRes val nameId: Int, @AttrRes val attrId: Int, @AttrRes val attrTextId: Int, val primary: Boolean, val secondary: Boolean, @StringRes val shortnameId: Int) {
        PRE(R.string.overview_show_predictions, R.attr.predictionColor, R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.prediction_shortname),
        TREAT(R.string.overview_show_treatments, R.attr.cobColor, R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.treatments_shortname),
        BAS(R.string.overview_show_basals, R.attr.basal, R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.basal_shortname),
        ABS(R.string.overview_show_absinsulin, R.attr.iobColor, R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.abs_insulin_shortname),
        IOB(R.string.overview_show_iob, R.attr.iobColor, R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.iob),
        COB(R.string.overview_show_cob, R.attr.cobColor, R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.cob),
        DEV(R.string.overview_show_deviations, R.attr.bgiColor, R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.deviation_shortname),
        BGI(R.string.overview_show_bgi, R.attr.bgiColor, R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.bgi_shortname),
        SEN(R.string.overview_show_sensitivity, R.attr.ratioColor, R.attr.menuTextColorInverse, primary = false, secondary = true, shortnameId = R.string.sensitivity_shortname),
        ACT(R.string.overview_show_activity, R.attr.activityColor, R.attr.menuTextColor, primary = true, secondary = false, shortnameId = R.string.activity_shortname),
        DEVSLOPE(R.string.overview_show_deviationslope, R.attr.devSlopePosColor, R.attr.menuTextColor, primary = false, secondary = true, shortnameId = R.string.devslope_shortname)
    }

    companion object {

        const val MAX_GRAPHS = 5 // including main
        const val SCALE_ID = 1001
    }

    fun enabledTypes(graph: Int): String {
        val r = StringBuilder()
        for (type in CharType.values()) if (_setting[graph][type.ordinal]) {
            r.append(rh.gs(type.shortnameId))
            r.append(" ")
        }
        return r.toString()
    }

    private var _setting: MutableList<Array<Boolean>> = ArrayList()

    val setting: List<Array<Boolean>>
        @Synchronized get() = _setting.toMutableList() // implicitly does a list copy

    @Synchronized
    private fun storeGraphConfig() {
        val sts = Gson().toJson(_setting)
        sp.putString(R.string.key_graphconfig, sts)
        aapsLogger.debug(sts)
    }

    @Synchronized
    fun loadGraphConfig() {
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

    fun setupChartMenu(context: Context, chartButton: ImageButton) {
        val settingsCopy = setting
        val numOfGraphs = settingsCopy.size // 1 main + x secondary

        chartButton.setOnClickListener { v: View ->
            val predictionsAvailable: Boolean = when {
                config.APS      -> loop.lastRun?.request?.hasPredictions ?: false
                config.NSCLIENT -> true
                else            -> false
            }
            val popup = PopupMenu(v.context, v)

            popup.menu.addSubMenu(Menu.NONE, SCALE_ID, Menu.NONE, rh.gs(R.string.graph_scale)).also {
                it.add(Menu.NONE, SCALE_ID + 6, Menu.NONE, "6")
                it.add(Menu.NONE, SCALE_ID + 12, Menu.NONE, "12")
                it.add(Menu.NONE, SCALE_ID + 18, Menu.NONE, "18")
                it.add(Menu.NONE, SCALE_ID + 24, Menu.NONE, "24")
            }

            val used = arrayListOf<Int>()

            for (g in 0 until numOfGraphs) {
                if (g != 0) {
                    val dividerItem = popup.menu.add(Menu.NONE, g, Menu.NONE, "------- ${rh.gs(R.string.graph_menu_divider_header)} $g -------")
                    dividerItem.isCheckable = true
                    dividerItem.isChecked = true
                }
                CharType.values().forEach { m ->
                    if (g == 0 && !m.primary) return@forEach
                    if (g > 0 && !m.secondary) return@forEach
                    var insert = true
                    if (m == CharType.PRE) insert = predictionsAvailable
                    if (m == CharType.DEVSLOPE) insert = buildHelper.isDev()
                    if (used.contains(m.ordinal)) insert = false
                    for (g2 in g + 1 until numOfGraphs) {
                        if (settingsCopy[g2][m.ordinal]) insert = false
                    }
                    if (insert) {
                        val item = popup.menu.add(Menu.NONE, m.ordinal + 100 * (g + 1), Menu.NONE, rh.gs(m.nameId))
                        val title = item.title
                        val s = SpannableString(" $title ")
                        s.setSpan(ForegroundColorSpan(rh.gac(m.attrTextId)), 0, s.length, 0)
                        s.setSpan(BackgroundColorSpan(rh.gac(m.attrId)), 0, s.length, 0)
                        item.title = s
                        item.isCheckable = true
                        item.isChecked = settingsCopy[g][m.ordinal]
                        if (settingsCopy[g][m.ordinal]) used.add(m.ordinal)
                    }
                }
            }
            if (numOfGraphs < MAX_GRAPHS) {
                val dividerItem = popup.menu.add(Menu.NONE, numOfGraphs, Menu.NONE, "------- ${rh.gs(R.string.graph_menu_divider_header)} $numOfGraphs -------")
                dividerItem.isCheckable = true
                dividerItem.isChecked = false
            }

            popup.setOnMenuItemClickListener {
                synchronized(this) {
                    try {
                        // id < 100 graph header - divider 1, 2, 3 .....
                        when {
                            it.itemId == SCALE_ID                              -> {
                                // do nothing, submenu
                            }

                            it.itemId > SCALE_ID && it.itemId < SCALE_ID + 100 -> {
                                val hours = it.itemId - SCALE_ID // 6,12,....
                                rxBus.send(EventScale(hours))
                            }

                            it.itemId == numOfGraphs                           -> {
                                // add new empty
                                _setting.add(Array(CharType.values().size) { false })
                            }

                            it.itemId < 100                                    -> {
                                // remove graph
                                _setting.removeAt(it.itemId)
                            }

                            else                                               -> {
                                val graphNumber = it.itemId / 100 - 1
                                val item = it.itemId % 100
                                _setting[graphNumber][item] = !it.isChecked
                            }
                        }
                    } catch (exception: Exception) {
                        fabricPrivacy.logException(exception)
                    }
                }
                storeGraphConfig()
                setupChartMenu(context, chartButton)
                rxBus.send(EventRefreshOverview("OnMenuItemClickListener", now = true))
                return@setOnMenuItemClickListener true
            }
            chartButton.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp)
            popup.setOnDismissListener { chartButton.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp) }
            popup.show()
        }
    }

    fun isEnabledIn(type: CharType): Int {
        val settingsCopy = setting
        val numOfGraphs = settingsCopy.size // 1 main + x secondary
        for (g in 0 until numOfGraphs) if (settingsCopy[g][type.ordinal]) return g
        return -1
    }

}