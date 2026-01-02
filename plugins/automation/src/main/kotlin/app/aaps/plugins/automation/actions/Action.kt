package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.triggers.Trigger
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider

abstract class Action(val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    var precondition: Trigger? = null

    abstract fun friendlyName(): Int
    abstract fun shortDescription(): String
    abstract fun doAction(callback: Callback)
    abstract fun isValid(): Boolean
    @DrawableRes abstract fun icon(): Int

    var title = ""

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    open fun generateDialog(root: LinearLayout) {}

    open fun hasDialog(): Boolean = false

    open fun toJSON(): String =
        JSONObject().put("type", this.javaClass.simpleName).toString()

    open fun fromJSON(data: String): Action = this

    fun apply(a: Action) {
        val obj = JSONObject(a.toJSON())
        val type = obj.getString("type")
        val data = obj.getJSONObject("data")
        if (type == javaClass.simpleName) fromJSON(data.toString())
    }

    fun instantiate(obj: JSONObject): Action? {
        try {
            var type = obj.getString("type")
            val data = if (obj.has("data")) obj.getJSONObject("data") else JSONObject()
            // stripe off package name
            val dotIndex = type.lastIndexOf('.')
            if (dotIndex > 0) type = type.substring(dotIndex + 1)
            return when (type) {
                ActionAlarm::class.java.simpleName                -> ActionAlarm(injector).fromJSON(data.toString())
                ActionSettingsExport::class.java.simpleName       -> ActionSettingsExport(injector).fromJSON(data.toString())
                ActionCarePortalEvent::class.java.simpleName      -> ActionCarePortalEvent(injector).fromJSON(data.toString())
                ActionDummy::class.java.simpleName                -> ActionDummy(injector).fromJSON(data.toString())
                ActionSMBChange::class.java.simpleName            -> ActionSMBChange(injector).fromJSON(data.toString())
                ActionNotification::class.java.simpleName         -> ActionNotification(injector).fromJSON(data.toString())
                ActionProfileSwitch::class.java.simpleName        -> ActionProfileSwitch(injector).fromJSON(data.toString())
                ActionProfileSwitchPercent::class.java.simpleName -> ActionProfileSwitchPercent(injector).fromJSON(data.toString())
                ActionRunAutotune::class.java.simpleName          -> ActionRunAutotune(injector).fromJSON(data.toString())
                ActionSendSMS::class.java.simpleName              -> ActionSendSMS(injector).fromJSON(data.toString())
                ActionStartTempTarget::class.java.simpleName      -> ActionStartTempTarget(injector).fromJSON(data.toString())
                ActionStopTempTarget::class.java.simpleName       -> ActionStopTempTarget(injector).fromJSON(data.toString())
                else                                              -> throw ClassNotFoundException(type)
            }
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
    }
}