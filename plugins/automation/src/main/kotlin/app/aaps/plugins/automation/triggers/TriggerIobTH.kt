package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import java.util.Optional
import dagger.android.HasAndroidInjector
import app.aaps.plugins.automation.R
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputIobTH
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject
import javax.inject.Inject

class TriggerIobTH(injector: HasAndroidInjector) : Trigger(injector) {

    @Inject lateinit var sp: SP
    //@Inject lateinit var receiverStatusStore: ReceiverStatusStore

    var IobTHpercent = InputIobTH()
    var comparator = Comparator(rh)

    // from TriggerWifiSsid: @Suppress("unused")
    constructor(injector: HasAndroidInjector, IobTHpercent: Double, compare: Comparator.Compare) : this(injector) {
        this.IobTHpercent = InputIobTH(IobTHpercent.toInt())
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerIobTH: TriggerIobTH) : this(injector) {
        this.IobTHpercent = InputIobTH(triggerIobTH.IobTHpercent.value)
        comparator = Comparator(rh, triggerIobTH.comparator.value)
    }

    fun setValue(IobTHpercent: Int): TriggerIobTH {
        this.IobTHpercent.value = IobTHpercent
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerIobTH {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val actualPercent = sp.getInt(R.string.iob_threshold_percent,100)
        if (comparator.value.check(actualPercent, IobTHpercent.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "set iob_threshold_percent ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "set iob_threshold_percent NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("iobTH_percent", IobTHpercent.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        IobTHpercent.value = JsonHelper.safeGetInt(d, "iobTH_percent")
        comparator.value = Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.autoisf_iobTH_percent

    override fun friendlyDescription(): String =
        rh.gs(R.string.iobTHpercentcompared, rh.gs(comparator.value.stringRes), IobTHpercent.value.toInt())

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_iobth)

    override fun duplicate(): Trigger = TriggerIobTH(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.autoisf_iobTH_percent, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.autoisf_iobTH_percent) + ": ", "%", IobTHpercent))
            .build(root)
    }
}