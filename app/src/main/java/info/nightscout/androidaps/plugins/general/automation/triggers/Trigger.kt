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
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseTriggerDialog
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerClone
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerRemove
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.services.LastLocationDataContainer
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

abstract class Trigger(val injector: HasAndroidInjector) {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var sp: SP
    @Inject lateinit var locationDataContainer: LastLocationDataContainer
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    init {
        injector.androidInjector().inject(this)
    }

    abstract fun shouldRun(): Boolean
    abstract fun toJSON(): String
    abstract fun fromJSON(data: String): Trigger

    abstract fun friendlyName(): Int
    abstract fun friendlyDescription(): String
    abstract fun icon(): Optional<Int?>
    abstract fun duplicate(): Trigger

    companion object {
        @JvmStatic
        fun scanForActivity(cont: Context?): AppCompatActivity? {
            when (cont) {
                null                 -> return null
                is AppCompatActivity -> return cont
                is ContextWrapper    -> return scanForActivity(cont.baseContext)
                else                 -> return null
            }
        }
    }

    open fun generateDialog(root: LinearLayout) {
        val title = TextView(root.context)
        title.setText(friendlyName())
        root.addView(title)
    }

    fun instantiate(obj: JSONObject): Trigger? {
        try {
            val type = obj.getString("type")
            val data = obj.getJSONObject("data")
            val clazz = Class.forName(type).kotlin
            return (clazz.primaryConstructor?.call(injector) as Trigger).fromJSON(data?.toString()
                ?: "")
        } catch (e: ClassNotFoundException) {
            aapsLogger.error("Unhandled exception", e)
        } catch (e: InstantiationException) {
            aapsLogger.error("Unhandled exception", e)
        } catch (e: IllegalAccessException) {
            aapsLogger.error("Unhandled exception", e)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
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