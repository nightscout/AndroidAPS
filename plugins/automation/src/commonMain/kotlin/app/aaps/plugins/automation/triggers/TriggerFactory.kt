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
import kotlinx.serialization.json.JsonObject

/**
 * Builds [Trigger]s from their stored JSON.
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
     * A new, empty trigger of the named type, or null when the name is not one of ours.
     * [type] may be a simple name or the full class name a previous version stored; the package is
     * dropped either way.
     * The list is written out rather than reflected over. `Class.forName` and `primaryConstructor`
     * are JVM only, so reflection would keep this file on Android for no other reason - and an
     * explicit list fails to compile when a trigger is renamed, where reflection would have gone on
     * returning null at runtime.
     */
    private fun empty(type: String): Trigger? = when (type.substringAfterLast('.')) {
        TriggerAutosensValue::class.simpleName      -> TriggerAutosensValue(deps)
        TriggerBg::class.simpleName                 -> TriggerBg(deps)
        TriggerBolusAgo::class.simpleName           -> TriggerBolusAgo(deps)
        TriggerBTDevice::class.simpleName           -> triggerBTDevice()
        TriggerSensorAge::class.simpleName          -> TriggerSensorAge(deps)
        TriggerCannulaAge::class.simpleName         -> TriggerCannulaAge(deps)
        TriggerPodChange::class.simpleName          -> TriggerPodChange(deps)
        TriggerInsulinAge::class.simpleName         -> TriggerInsulinAge(deps)
        TriggerReservoirLevel::class.simpleName     -> TriggerReservoirLevel(deps)
        TriggerPumpBatteryAge::class.simpleName     -> TriggerPumpBatteryAge(deps)
        TriggerPumpBatteryLevel::class.simpleName   -> TriggerPumpBatteryLevel(deps)
        TriggerIob::class.simpleName                -> TriggerIob(deps)
        TriggerCOB::class.simpleName                -> TriggerCOB(deps)
        // The connector is the only one that rebuilds children, and only this factory can name a
        // trigger class, so it is handed the way back in.
        TriggerConnector::class.simpleName          -> TriggerConnector(deps).also { it.childFromJson = ::instantiate }
        TriggerDelta::class.simpleName              -> TriggerDelta(deps)
        TriggerDummy::class.simpleName              -> TriggerDummy(deps)
        TriggerHeartRate::class.simpleName          -> TriggerHeartRate(deps)
        TriggerLocation::class.simpleName           -> TriggerLocation(deps)
        TriggerProfilePercent::class.simpleName     -> TriggerProfilePercent(deps)
        TriggerPumpLastConnection::class.simpleName -> TriggerPumpLastConnection(deps)
        TriggerRecurringTime::class.simpleName      -> TriggerRecurringTime(deps)
        TriggerSceneActive::class.simpleName        -> triggerSceneActive()
        TriggerTempTarget::class.simpleName         -> TriggerTempTarget(deps)
        TriggerTempTargetValue::class.simpleName    -> TriggerTempTargetValue(deps)
        TriggerTime::class.simpleName               -> TriggerTime(deps)
        TriggerTimeRange::class.simpleName          -> TriggerTimeRange(deps)
        TriggerWifiSsid::class.simpleName           -> triggerWifiSsid()
        TriggerStepsCount::class.simpleName         -> TriggerStepsCount(deps)

        else                                        -> null
    }

    /** Builds an empty trigger from its class name, for the "add trigger" list in the editor. */
    fun instantiate(className: String): Trigger? = empty(className)

    fun instantiate(obj: JsonObject): Trigger {
        try {
            val type = obj.lenientString("type")
            val data = obj["data"] as? JsonObject ?: JsonObject(emptyMap())
            // An unknown type reads as an empty connector, which is what it always did - a rule
            // written by a newer version must not take the whole list down.
            return empty(type)?.fromJSON(data.toString()) ?: TriggerConnector(deps)
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Error parsing $obj : $e")
        }
        return TriggerConnector(deps)
    }
}
