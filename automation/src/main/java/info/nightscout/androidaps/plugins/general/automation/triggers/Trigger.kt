package info.nightscout.androidaps.plugins.general.automation.triggers

import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseTriggerDialog
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerClone
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerRemove
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.services.LastLocationDataContainer
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject

abstract class Trigger(val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var sp: SP
    @Inject lateinit var locationDataContainer: LastLocationDataContainer
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var dateUtil: DateUtil

    init {
        injector.androidInjector().inject(this)
    }

    abstract fun shouldRun(): Boolean
    abstract fun dataJSON(): JSONObject
    abstract fun fromJSON(data: String): Trigger

    abstract fun friendlyName(): Int
    abstract fun friendlyDescription(): String
    abstract fun icon(): Optional<Int?>
    abstract fun duplicate(): Trigger

    companion object {

        @JvmStatic
        fun scanForActivity(cont: Context?): AppCompatActivity? {
            return when (cont) {
                null                 -> null
                is AppCompatActivity -> cont
                is ContextWrapper    -> scanForActivity(cont.baseContext)
                else                 -> null
            }
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

    fun instantiate(obj: JSONObject): Trigger? {
        val type = obj.getString("type")
        val data = obj.getJSONObject("data")
        //val clazz = Class.forName(type).kotlin
        //return (clazz.primaryConstructor?.call(injector) as Trigger).fromJSON(data?.toString() ?: "")
        return when (type) {
            TriggerAutosensValue::class.java.name,              // backward compatibility
            TriggerAutosensValue::class.java.simpleName      -> TriggerAutosensValue(injector).fromJSON(data?.toString() ?: "")
            TriggerBg::class.java.name,
            TriggerBg::class.java.simpleName                 -> TriggerBg(injector).fromJSON(data?.toString() ?: "")
            TriggerBolusAgo::class.java.name,
            TriggerBolusAgo::class.java.simpleName           -> TriggerBolusAgo(injector).fromJSON(data?.toString() ?: "")
            TriggerBTDevice::class.java.name,
            TriggerBTDevice::class.java.simpleName           -> TriggerBTDevice(injector).fromJSON(data?.toString() ?: "")
            TriggerIob::class.java.name,
            TriggerIob::class.java.simpleName                -> TriggerIob(injector).fromJSON(data?.toString() ?: "")
            TriggerCOB::class.java.name,
            TriggerCOB::class.java.simpleName                -> TriggerCOB(injector).fromJSON(data?.toString() ?: "")
            TriggerConnector::class.java.name,
            TriggerConnector::class.java.simpleName          -> TriggerConnector(injector).fromJSON(data?.toString() ?: "")
            TriggerDelta::class.java.name,
            TriggerDelta::class.java.simpleName              -> TriggerDelta(injector).fromJSON(data?.toString() ?: "")
            TriggerDummy::class.java.name,
            TriggerDummy::class.java.simpleName              -> TriggerDummy(injector).fromJSON(data?.toString() ?: "")
            TriggerIob::class.java.name,
            TriggerIob::class.java.simpleName                -> TriggerIob(injector).fromJSON(data?.toString() ?: "")
            TriggerLocation::class.java.name,
            TriggerLocation::class.java.simpleName           -> TriggerLocation(injector).fromJSON(data?.toString() ?: "")
            TriggerProfilePercent::class.java.name,
            TriggerProfilePercent::class.java.simpleName     -> TriggerProfilePercent(injector).fromJSON(data?.toString() ?: "")
            TriggerPumpLastConnection::class.java.name,
            TriggerPumpLastConnection::class.java.simpleName -> TriggerPumpLastConnection(injector).fromJSON(data?.toString() ?: "")
            TriggerRecurringTime::class.java.name,
            TriggerRecurringTime::class.java.simpleName      -> TriggerRecurringTime(injector).fromJSON(data?.toString() ?: "")
            TriggerTempTarget::class.java.name,
            TriggerTempTarget::class.java.simpleName         -> TriggerTempTarget(injector).fromJSON(data?.toString() ?: "")
            TriggerTime::class.java.name,
            TriggerTime::class.java.simpleName               -> TriggerTime(injector).fromJSON(data?.toString() ?: "")
            TriggerTimeRange::class.java.name,
            TriggerTimeRange::class.java.simpleName          -> TriggerTimeRange(injector).fromJSON(data?.toString() ?: "")
            TriggerWifiSsid::class.java.name,
            TriggerWifiSsid::class.java.simpleName           -> TriggerWifiSsid(injector).fromJSON(data?.toString() ?: "")
            else                                             -> throw ClassNotFoundException(type)
        }
    }

    fun createAddButton(context: Context, trigger: TriggerConnector): ImageButton {
        // Button [+]
        val buttonAdd = ImageButton(context)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        buttonAdd.layoutParams = params
        buttonAdd.setImageResource(R.drawable.ic_add)
        buttonAdd.contentDescription = resourceHelper.gs(R.string.add_short)
        buttonAdd.setOnClickListener {
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
        return buttonAdd
    }

    fun createDeleteButton(context: Context, trigger: Trigger): ImageButton {
        // Button [-]
        val buttonRemove = ImageButton(context)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        buttonRemove.layoutParams = params
        buttonRemove.setImageResource(R.drawable.ic_remove)
        buttonRemove.contentDescription = resourceHelper.gs(R.string.delete_short)
        buttonRemove.setOnClickListener {
            rxBus.send(EventTriggerRemove(trigger))
        }
        return buttonRemove
    }

    fun createCloneButton(context: Context, trigger: Trigger): ImageButton {
        // Button [*]
        val buttonClone = ImageButton(context)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        buttonClone.layoutParams = params
        buttonClone.setImageResource(R.drawable.ic_clone)
        buttonClone.contentDescription = resourceHelper.gs(R.string.copy_short)
        buttonClone.setOnClickListener {
            rxBus.send(EventTriggerClone(trigger))
        }
        return buttonClone
    }
}