package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

abstract class Action(val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    var precondition: Trigger? = null

    abstract fun friendlyName(): Int
    abstract fun shortDescription(): String
    abstract fun doAction(callback: Callback)
    abstract fun isValid(): Boolean
    @DrawableRes abstract fun icon(): Int

    var title = ""

    init {
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
        if (type == javaClass.name) fromJSON(data.toString())
    }

    fun instantiate(obj: JSONObject): Action? {
        try {
            val type = obj.getString("type")
            val data = if (obj.has("data")) obj.getJSONObject("data") else JSONObject()
            return when (type) {
                ActionAlarm::class.java.name,              // backward compatibility
                ActionAlarm::class.java.simpleName                -> ActionAlarm(injector).fromJSON(data.toString())
                ActionCarePortalEvent::class.java.name,
                ActionCarePortalEvent::class.java.simpleName      -> ActionCarePortalEvent(injector).fromJSON(data.toString())
                ActionDummy::class.java.name,
                ActionDummy::class.java.simpleName                -> ActionDummy(injector).fromJSON(data.toString())
                ActionLoopDisable::class.java.name,
                ActionLoopDisable::class.java.simpleName          -> ActionLoopDisable(injector).fromJSON(data.toString())
                ActionLoopEnable::class.java.name,
                ActionLoopEnable::class.java.simpleName           -> ActionLoopEnable(injector).fromJSON(data.toString())
                ActionLoopResume::class.java.name,
                ActionLoopResume::class.java.simpleName           -> ActionLoopResume(injector).fromJSON(data.toString())
                ActionLoopSuspend::class.java.name,
                ActionLoopSuspend::class.java.simpleName          -> ActionLoopSuspend(injector).fromJSON(data.toString())
                ActionNotification::class.java.name,
                ActionNotification::class.java.simpleName         -> ActionNotification(injector).fromJSON(data.toString())
                ActionProfileSwitch::class.java.name,
                ActionProfileSwitch::class.java.simpleName        -> ActionProfileSwitch(injector).fromJSON(data.toString())
                ActionProfileSwitchPercent::class.java.name,
                ActionProfileSwitchPercent::class.java.simpleName -> ActionProfileSwitchPercent(injector).fromJSON(data.toString())
                ActionSendSMS::class.java.name,
                ActionSendSMS::class.java.simpleName              -> ActionSendSMS(injector).fromJSON(data.toString())
                ActionStartTempTarget::class.java.name,
                ActionStartTempTarget::class.java.simpleName      -> ActionStartTempTarget(injector).fromJSON(data.toString())
                ActionStopTempTarget::class.java.name,
                ActionStopTempTarget::class.java.simpleName       -> ActionStopTempTarget(injector).fromJSON(data.toString())
                else                                              -> throw ClassNotFoundException(type)
            }
            //val clazz = Class.forName(type).kotlin
            //return (clazz.primaryConstructor?.call(injector) as Action).fromJSON(data?.toString()
            //    ?: "")
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