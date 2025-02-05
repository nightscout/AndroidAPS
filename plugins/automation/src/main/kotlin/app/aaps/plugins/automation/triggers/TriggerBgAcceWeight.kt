package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import java.util.Optional
import dagger.android.HasAndroidInjector
import app.aaps.plugins.automation.R
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputWeight
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import org.json.JSONObject
import app.aaps.core.utils.JsonHelper
import javax.inject.Inject

class TriggerBgAcceWeight(injector: HasAndroidInjector) : Trigger(injector) {

    @Inject lateinit var sp: SP

    var acceWeight = InputWeight()
    var comparator = Comparator(rh)

    // from TriggerWifiSsid: @Suppress("unused")
    constructor(injector: HasAndroidInjector, acceWeight: Double, compare: Comparator.Compare) : this(injector) {
        this.acceWeight = InputWeight(acceWeight)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerBgAcceWeight: TriggerBgAcceWeight) : this(injector) {
        this.acceWeight = InputWeight(triggerBgAcceWeight.acceWeight.value)
        comparator = Comparator(rh, triggerBgAcceWeight.comparator.value)
    }

    fun setValue(acceWeight: Double): TriggerBgAcceWeight {
        this.acceWeight.value = acceWeight
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerBgAcceWeight {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val actualWeight = sp.getDouble(R.string.bgAccel_ISF_weight
            ,0.0)
        if (comparator.value.check(actualWeight, acceWeight.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "set bgAccel_ISF_weight ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "set bgAccel_ISF_weight NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("acce_weight", acceWeight.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        acceWeight.value = JsonHelper.safeGetDouble(d, "acce_weight")
        comparator.value = Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.autoisf_acce_weight

    override fun friendlyDescription(): String =
        rh.gs(R.string.acceweightcompared, rh.gs(comparator.value.stringRes), acceWeight.value)

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_acce_weight)

    override fun duplicate(): Trigger = TriggerBgAcceWeight(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.autoisf_acce_weight, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.autoisf_acce_weight) + ": ", "", acceWeight))
            .build(root)
    }
}