package app.aaps.plugins.automation.triggers

import android.content.Context
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.BtConnectionSource
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.full.primaryConstructor

/**
 * Builds [Trigger]s from their stored JSON.
 *
 * Same reason as [app.aaps.plugins.automation.actions.ActionFactory]: triggers are named by type in
 * the persisted automation list, so they cannot be constructed by Dagger, and they used to inject
 * themselves through `HasAndroidInjector` - which needs generated Java and so cannot happen in a
 * multiplatform module.
 *
 * Most triggers need only [TriggerDeps]. The three that need more take it on their own constructor.
 */
@Singleton
class TriggerFactory @Inject constructor(
    val deps: TriggerDeps,
    private val context: Context,
    // Provider breaks a cycle: BtConnectionSource is AutomationRuntime, which reaches this factory.
    private val btConnectionSource: Provider<BtConnectionSource>,
    private val sceneApi: SceneAutomationApi,
    private val receiverStatusStore: ReceiverStatusStore
) {

    private val aapsLogger get() = deps.aapsLogger

    /// The three triggers that need more than [TriggerDeps]; everything else takes deps alone.
    fun triggerBTDevice() = TriggerBTDevice(deps, context, btConnectionSource.get())
    fun triggerWifiSsid() = TriggerWifiSsid(deps, receiverStatusStore)
    fun triggerSceneActive() = TriggerSceneActive(deps, sceneApi)

    /**
     * Builds an empty trigger from its class name, for the "add trigger" list in the editor.
     *
     * Reflection is used for the many triggers that take only [deps]; the three that need more are
     * named explicitly, because `primaryConstructor.call(deps)` would fail on them and the old code
     * would have swallowed that as a null.
     */
    fun instantiate(className: String): Trigger? = runCatching {
        val k = Class.forName(className).kotlin
        when (k) {
            TriggerBTDevice::class    -> triggerBTDevice()
            TriggerWifiSsid::class    -> triggerWifiSsid()
            TriggerSceneActive::class -> triggerSceneActive()
            else                      -> k.primaryConstructor?.call(deps) as? Trigger
        }
    }.getOrNull()

    fun instantiate(obj: JsonObject): Trigger {
        try {
            var type = obj.lenientString("type")
            val data = obj["data"] as? JsonObject ?: JsonObject(emptyMap())
            // stripe off package name
            val dotIndex = type.lastIndexOf('.')
            if (dotIndex > 0) type = type.substring(dotIndex + 1)
            return when (type) {
                TriggerAutosensValue::class.java.simpleName      -> TriggerAutosensValue(deps).fromJSON(data.toString())
                TriggerBg::class.java.simpleName                 -> TriggerBg(deps).fromJSON(data.toString())
                TriggerBolusAgo::class.java.simpleName           -> TriggerBolusAgo(deps).fromJSON(data.toString())
                TriggerBTDevice::class.java.simpleName           -> triggerBTDevice().fromJSON(data.toString())
                TriggerSensorAge::class.java.simpleName          -> TriggerSensorAge(deps).fromJSON(data.toString())
                TriggerCannulaAge::class.java.simpleName         -> TriggerCannulaAge(deps).fromJSON(data.toString())
                TriggerPodChange::class.java.simpleName          -> TriggerPodChange(deps).fromJSON(data.toString())
                TriggerInsulinAge::class.java.simpleName         -> TriggerInsulinAge(deps).fromJSON(data.toString())
                TriggerReservoirLevel::class.java.simpleName     -> TriggerReservoirLevel(deps).fromJSON(data.toString())
                TriggerPumpBatteryAge::class.java.simpleName     -> TriggerPumpBatteryAge(deps).fromJSON(data.toString())
                TriggerPumpBatteryLevel::class.java.simpleName   -> TriggerPumpBatteryLevel(deps).fromJSON(data.toString())
                TriggerIob::class.java.simpleName                -> TriggerIob(deps).fromJSON(data.toString())
                TriggerCOB::class.java.simpleName                -> TriggerCOB(deps).fromJSON(data.toString())
                TriggerConnector::class.java.simpleName          -> TriggerConnector(deps).fromJSON(data.toString())
                TriggerDelta::class.java.simpleName              -> TriggerDelta(deps).fromJSON(data.toString())
                TriggerDummy::class.java.simpleName              -> TriggerDummy(deps).fromJSON(data.toString())
                TriggerHeartRate::class.java.simpleName          -> TriggerHeartRate(deps).fromJSON(data.toString())
                TriggerLocation::class.java.simpleName           -> TriggerLocation(deps).fromJSON(data.toString())
                TriggerProfilePercent::class.java.simpleName     -> TriggerProfilePercent(deps).fromJSON(data.toString())
                TriggerPumpLastConnection::class.java.simpleName -> TriggerPumpLastConnection(deps).fromJSON(data.toString())
                TriggerRecurringTime::class.java.simpleName      -> TriggerRecurringTime(deps).fromJSON(data.toString())
                TriggerSceneActive::class.java.simpleName        -> triggerSceneActive().fromJSON(data.toString())
                TriggerTempTarget::class.java.simpleName         -> TriggerTempTarget(deps).fromJSON(data.toString())
                TriggerTempTargetValue::class.java.simpleName    -> TriggerTempTargetValue(deps).fromJSON(data.toString())
                TriggerTime::class.java.simpleName               -> TriggerTime(deps).fromJSON(data.toString())
                TriggerTimeRange::class.java.simpleName          -> TriggerTimeRange(deps).fromJSON(data.toString())
                TriggerWifiSsid::class.java.simpleName           -> triggerWifiSsid().fromJSON(data.toString())
                TriggerStepsCount::class.java.simpleName         -> TriggerStepsCount(deps).fromJSON(data.toString())

                else                                             -> TriggerConnector(deps)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Error parsing $obj : $e")
        }
        return TriggerConnector(deps)
    }
}
