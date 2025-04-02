package app.aaps.plugins.automation.triggers

import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.dialogs.ChooseTriggerDialog
import app.aaps.plugins.automation.events.EventTriggerChanged
import app.aaps.plugins.automation.events.EventTriggerClone
import app.aaps.plugins.automation.events.EventTriggerRemove
import app.aaps.plugins.automation.services.LastLocationDataContainer
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional
import javax.inject.Inject

abstract class Trigger(val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var locationDataContainer: LastLocationDataContainer
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var dateUtil: DateUtil

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    abstract fun shouldRun(): Boolean
    abstract fun dataJSON(): JSONObject
    abstract fun fromJSON(data: String): Trigger

    abstract fun friendlyName(): Int
    abstract fun friendlyDescription(): String
    abstract fun icon(): Optional<Int>
    abstract fun duplicate(): Trigger

    fun scanForActivity(cont: Context?): AppCompatActivity? {
        return when (cont) {
            null                 -> null
            is AppCompatActivity -> cont
            is ContextWrapper    -> scanForActivity(cont.baseContext)
            else                 -> null
        }
    }

    open fun generateDialog(root: LinearLayout) {
        val title = TextView(root.context)
        title.setText(friendlyName())
        root.addView(title)
    }

    fun toJSON(): String =
        JSONObject()
            .put("type", this::class.java.simpleName)
            .put("data", dataJSON())
            .toString()

    fun instantiate(obj: JSONObject): Trigger {
        try {
            var type = obj.getString("type")
            val data = obj.getJSONObject("data")
            // stripe off package name
            val dotIndex = type.lastIndexOf('.')
            if (dotIndex > 0) type = type.substring(dotIndex + 1)
            return when (type) {
                TriggerAutosensValue::class.java.simpleName      -> TriggerAutosensValue(injector).fromJSON(data.toString())
                TriggerBg::class.java.simpleName                 -> TriggerBg(injector).fromJSON(data.toString())
                TriggerBgAcceWeight::class.java.simpleName       -> TriggerBgAcceWeight(injector).fromJSON(data.toString())
                TriggerBolusAgo::class.java.simpleName           -> TriggerBolusAgo(injector).fromJSON(data.toString())
                TriggerBTDevice::class.java.simpleName           -> TriggerBTDevice(injector).fromJSON(data.toString())
                TriggerSensorAge::class.java.simpleName          -> TriggerSensorAge(injector).fromJSON(data.toString())
                TriggerCannulaAge::class.java.simpleName         -> TriggerCannulaAge(injector).fromJSON(data.toString())
                TriggerPodChange::class.java.simpleName          -> TriggerPodChange(injector).fromJSON(data.toString())
                TriggerInsulinAge::class.java.simpleName         -> TriggerInsulinAge(injector).fromJSON(data.toString())
                TriggerReservoirLevel::class.java.simpleName     -> TriggerReservoirLevel(injector).fromJSON(data.toString())
                TriggerPumpBatteryAge::class.java.simpleName     -> TriggerPumpBatteryAge(injector).fromJSON(data.toString())
                TriggerPumpBatteryLevel::class.java.simpleName   -> TriggerPumpBatteryLevel(injector).fromJSON(data.toString())
                TriggerIob::class.java.simpleName                -> TriggerIob(injector).fromJSON(data.toString())
                TriggerIobTH::class.java.simpleName              -> TriggerIobTH(injector).fromJSON(data.toString())
                TriggerCOB::class.java.simpleName                -> TriggerCOB(injector).fromJSON(data.toString())
                TriggerConnector::class.java.simpleName          -> TriggerConnector(injector).fromJSON(data.toString())
                TriggerDelta::class.java.simpleName              -> TriggerDelta(injector).fromJSON(data.toString())
                TriggerDummy::class.java.simpleName              -> TriggerDummy(injector).fromJSON(data.toString())
                TriggerHeartRate::class.java.simpleName          -> TriggerHeartRate(injector).fromJSON(data.toString())
                TriggerLocation::class.java.simpleName           -> TriggerLocation(injector).fromJSON(data.toString())
                TriggerProfilePercent::class.java.simpleName     -> TriggerProfilePercent(injector).fromJSON(data.toString())
                TriggerPumpLastConnection::class.java.simpleName -> TriggerPumpLastConnection(injector).fromJSON(data.toString())
                TriggerRecurringTime::class.java.simpleName      -> TriggerRecurringTime(injector).fromJSON(data.toString())
                TriggerTempTarget::class.java.simpleName         -> TriggerTempTarget(injector).fromJSON(data.toString())
                TriggerTempTargetValue::class.java.simpleName    -> TriggerTempTargetValue(injector).fromJSON(data.toString())
                TriggerTime::class.java.simpleName               -> TriggerTime(injector).fromJSON(data.toString())
                TriggerTimeRange::class.java.simpleName          -> TriggerTimeRange(injector).fromJSON(data.toString())
                TriggerWifiSsid::class.java.simpleName           -> TriggerWifiSsid(injector).fromJSON(data.toString())
                TriggerStepsCount::class.java.simpleName         -> TriggerStepsCount(injector).fromJSON(data.toString())

                else                                             -> TriggerConnector(injector)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Error parsing $obj")
        }
        return TriggerConnector(injector)
    }

    fun createAddButton(context: Context, trigger: TriggerConnector): ImageButton =
        // Button [+]
        ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(app.aaps.core.objects.R.drawable.ic_add)
            contentDescription = rh.gs(R.string.add_short)
            setOnClickListener {
                scanForActivity(context)?.supportFragmentManager?.let {
                    val dialog = ChooseTriggerDialog()
                    dialog.show(it, "ChooseTriggerDialog")
                    dialog.setOnClickListener(object : ChooseTriggerDialog.OnClickListener {
                        override fun onClick(newTriggerObject: Trigger) {
                            trigger.list.add(newTriggerObject)
                            rxBus.send(EventTriggerChanged())
                        }
                    })
                }
            }
        }

    fun createDeleteButton(context: Context, trigger: Trigger): ImageButton =
        // Button [-]
        ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(app.aaps.core.objects.R.drawable.ic_remove)
            contentDescription = rh.gs(R.string.delete_short)
            setOnClickListener {
                rxBus.send(EventTriggerRemove(trigger))
            }
        }

    fun createCloneButton(context: Context, trigger: Trigger): ImageButton =
        // Button [*]
        ImageButton(context).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = params
            setImageResource(app.aaps.core.objects.R.drawable.ic_clone)
            contentDescription = rh.gs(R.string.copy_short)
            setOnClickListener {
                rxBus.send(EventTriggerClone(trigger))
            }
        }
}