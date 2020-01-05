package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

abstract class Action(val mainApp: MainApp) {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin

    var precondition: Trigger? = null

    abstract fun friendlyName(): Int
    abstract fun shortDescription(): String
    abstract fun doAction(callback: Callback)
    @DrawableRes abstract fun icon(): Int

    init {
        mainApp.androidInjector().inject(this)
    }

    open fun generateDialog(root: LinearLayout) {}

    open fun hasDialog(): Boolean = false

    open fun toJSON(): String =
        JSONObject().put("type", this.javaClass.name).toString()

    open fun fromJSON(data: String): Action = this

    fun apply(a: Action) {
        val obj = JSONObject(a.toJSON())
        val type = obj.getString("type")
        val data = obj.getJSONObject("data")
        if (type == javaClass.name) fromJSON(data.toString())
    }

    fun instantiate(obj: JSONObject): Action? {
        try {
            val type = obj.getString("type")
            val data = obj.optJSONObject("data")
            val clazz = Class.forName(type).kotlin
            return (clazz.primaryConstructor?.call(mainApp) as Action).fromJSON(data?.toString()
                ?: "")
            //return (clazz.newInstance() as Action).fromJSON(data?.toString() ?: "")
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
}