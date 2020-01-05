package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputBg
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerBg(mainApp: MainApp) : Trigger(mainApp) {
    private var bg = InputBg(mainApp)
    var comparator = Comparator(mainApp)

    constructor(mainApp: MainApp, value : Double, units : String, compare: Comparator.Compare) : this(mainApp){
        bg = InputBg(mainApp, value, units)
        comparator = Comparator(mainApp, compare)
    }

    constructor(mainApp: MainApp, triggerBg: TriggerBg) : this(mainApp){
        bg = InputBg(mainApp, triggerBg.bg.value, triggerBg.bg.units)
        comparator = Comparator(mainApp, triggerBg.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val glucoseStatus = GlucoseStatus.getGlucoseStatusData()
        if (glucoseStatus == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (glucoseStatus == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(glucoseStatus.glucose, Profile.toMgdl(bg.value, bg.units))) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("bg", bg.value)
            .put("comparator", comparator.value.toString())
            .put("units", bg.units)
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        bg.setUnits(JsonHelper.safeGetString(d, "units")!!)
        bg.value = JsonHelper.safeGetDouble(d, "bg")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.glucose

    override fun friendlyDescription(): String {
        return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE)
            resourceHelper.gs(R.string.glucoseisnotavailable)
        else
            resourceHelper.gs(if (bg.units == Constants.MGDL) R.string.glucosecomparedmgdl else R.string.glucosecomparedmmol, resourceHelper.gs(comparator.value.stringRes), bg.value, bg.units)
    }

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.icon_cp_bgcheck)

    override fun duplicate(): Trigger = TriggerBg(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.glucose))
            .add(comparator)
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.glucose_u, bg.units), "", bg))
            .build(root)
    }
}