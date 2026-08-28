package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.BtConnectionSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.full.primaryConstructor
import kotlinx.serialization.json.JsonObject

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
@SingleIn(AppScope::class)
class TriggerFactory @Inject constructor(
    val deps: TriggerDeps,
    // Provider breaks a cycle: BtConnectionSource is AutomationRuntime, which reaches this factory.
    private val btConnectionSource: Provider<BtConnectionSource>,
    private val sceneApi: SceneAutomationApi,
    private val receiverStatusStore: ReceiverStatusStore
) {

    private val aapsLogger get() = deps.aapsLogger

    /// The three triggers that need more than [TriggerDeps]; everything else takes deps alone.
    fun triggerBTDevice() = TriggerBTDevice(deps, btConnectionSource())
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
                TriggerAutosensValue::class.simpleName      -> TriggerAutosensValue(deps).fromJSON(data.toString())
                TriggerBg::class.simpleName                 -> TriggerBg(deps).fromJSON(data.toString())
                TriggerBolusAgo::class.simpleName           -> TriggerBolusAgo(deps).fromJSON(data.toString())
                TriggerBTDevice::class.simpleName           -> triggerBTDevice().fromJSON(data.toString())
                TriggerSensorAge::class.simpleName          -> TriggerSensorAge(deps).fromJSON(data.toString())
                TriggerCannulaAge::class.simpleName         -> TriggerCannulaAge(deps).fromJSON(data.toString())
                TriggerPodChange::class.simpleName          -> TriggerPodChange(deps).fromJSON(data.toString())
                TriggerInsulinAge::class.simpleName         -> TriggerInsulinAge(deps).fromJSON(data.toString())
                TriggerReservoirLevel::class.simpleName     -> TriggerReservoirLevel(deps).fromJSON(data.toString())
                TriggerPumpBatteryAge::class.simpleName     -> TriggerPumpBatteryAge(deps).fromJSON(data.toString())
                TriggerPumpBatteryLevel::class.simpleName   -> TriggerPumpBatteryLevel(deps).fromJSON(data.toString())
                TriggerIob::class.simpleName                -> TriggerIob(deps).fromJSON(data.toString())
                TriggerCOB::class.simpleName                -> TriggerCOB(deps).fromJSON(data.toString())
                TriggerConnector::class.simpleName          -> TriggerConnector(deps).also { it.childFromJson = ::instantiate }.fromJSON(data.toString())
                TriggerDelta::class.simpleName              -> TriggerDelta(deps).fromJSON(data.toString())
                TriggerDummy::class.simpleName              -> TriggerDummy(deps).fromJSON(data.toString())
                TriggerHeartRate::class.simpleName          -> TriggerHeartRate(deps).fromJSON(data.toString())
                TriggerLocation::class.simpleName           -> TriggerLocation(deps).fromJSON(data.toString())
                TriggerProfilePercent::class.simpleName     -> TriggerProfilePercent(deps).fromJSON(data.toString())
                TriggerPumpLastConnection::class.simpleName -> TriggerPumpLastConnection(deps).fromJSON(data.toString())
                TriggerRecurringTime::class.simpleName      -> TriggerRecurringTime(deps).fromJSON(data.toString())
                TriggerSceneActive::class.simpleName        -> triggerSceneActive().fromJSON(data.toString())
                TriggerTempTarget::class.simpleName         -> TriggerTempTarget(deps).fromJSON(data.toString())
                TriggerTempTargetValue::class.simpleName    -> TriggerTempTargetValue(deps).fromJSON(data.toString())
                TriggerTime::class.simpleName               -> TriggerTime(deps).fromJSON(data.toString())
                TriggerTimeRange::class.simpleName          -> TriggerTimeRange(deps).fromJSON(data.toString())
                TriggerWifiSsid::class.simpleName           -> triggerWifiSsid().fromJSON(data.toString())
                TriggerStepsCount::class.simpleName         -> TriggerStepsCount(deps).fromJSON(data.toString())

                else                                             -> TriggerConnector(deps)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Error parsing $obj : $e")
        }
        return TriggerConnector(deps)
    }
}
