package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.triggers.TriggerDeps
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonObject

/**
 * Builds [Action]s from their stored JSON.
 *
 * Automations are persisted as a JSON string in which each action names its type, so actions cannot
 * be constructed by Dagger. They used to be handed a `HasAndroidInjector` and inject themselves,
 * which needed a generated members injector - Java, and therefore impossible in a multiplatform
 * module - and hid each action's real dependencies behind `@Inject lateinit`.
 *
 * This holds the dependencies instead and passes each action exactly what it asks for. The union
 * below looks wide, but it is the same set that was reachable through the injector before; the
 * difference is that it is now visible, and each action's constructor states its own needs.
 */
@SingleIn(AppScope::class)
class ActionFactory @Inject constructor(
    private val triggerDeps: TriggerDeps,
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val reminderScheduler: ReminderScheduler,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val profileUtil: ProfileUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val notificationManager: NotificationManager,
    private val activePlugin: ActivePlugin,
    private val preferences: Preferences,
    private val sceneApi: SceneAutomationApi,
    private val sceneIconResolver: SceneIconResolver,
    private val smsCommunicator: SmsCommunicator,
    private val autotunePlugin: Autotune,
    private val importExportPrefs: ImportExportPrefs,
    private val exportPasswordDataStore: ExportPasswordDataStore
) {

    /** A new, empty instance of every action type, for the "choose an action" list. */
    fun allActions(): List<Action> = listOf(
        actionAlarm(), actionCarePortalEvent(), actionDisableScene(), actionEnableScene(),
        actionNotification(), actionProfileSwitch(), actionProfileSwitchPercent(), actionRunAutotune(),
        actionRunScene(), actionSendSMS(), actionSettingsExport(), actionSMBChange(),
        actionStartTempTarget(), actionStopProcessing(), actionStopTempTarget()
    )

    fun actionAlarm() = ActionAlarm(aapsLogger, rh, pumpEnactResultProvider, rxBus, dateUtil, reminderScheduler, config)
    fun actionAlarm(text: String) = ActionAlarm(aapsLogger, rh, pumpEnactResultProvider, rxBus, dateUtil, reminderScheduler, config, text)
    fun actionCarePortalEvent() = ActionCarePortalEvent(aapsLogger, rh, pumpEnactResultProvider, persistenceLayer, profileFunction, dateUtil, glucoseStatusProvider)
    fun actionDisableScene() = ActionDisableScene(aapsLogger, rh, pumpEnactResultProvider, sceneApi, sceneIconResolver)
    fun actionDummy() = ActionDummy(aapsLogger, rh, pumpEnactResultProvider)
    fun actionEnableScene() = ActionEnableScene(aapsLogger, rh, pumpEnactResultProvider, sceneApi, sceneIconResolver)
    fun actionNotification() = ActionNotification(aapsLogger, rh, pumpEnactResultProvider, rxBus, notificationManager, persistenceLayer, dateUtil)
    fun actionProfileSwitch() = ActionProfileSwitch(aapsLogger, rh, pumpEnactResultProvider, profileRepository, profileFunction, dateUtil)
    fun actionProfileSwitchPercent() = ActionProfileSwitchPercent(aapsLogger, rh, pumpEnactResultProvider, profileFunction, triggerDeps)
    fun actionRunAutotune() = ActionRunAutotune(aapsLogger, rh, pumpEnactResultProvider, rh, autotunePlugin, profileFunction, activePlugin, preferences)
    fun actionRunScene() = ActionRunScene(aapsLogger, rh, pumpEnactResultProvider, sceneApi, sceneIconResolver, triggerDeps)
    fun actionSendSMS() = ActionSendSMS(aapsLogger, rh, pumpEnactResultProvider, smsCommunicator)
    fun actionSettingsExport() =
        ActionSettingsExport(
            aapsLogger, rh, pumpEnactResultProvider, rxBus, notificationManager, dateUtil, config,
            persistenceLayer, importExportPrefs, exportPasswordDataStore, preferences
        )

    fun actionSMBChange() = ActionSMBChange(aapsLogger, rh, pumpEnactResultProvider, dateUtil, preferences)
    fun actionStartTempTarget() = ActionStartTempTarget(aapsLogger, rh, pumpEnactResultProvider, activePlugin, persistenceLayer, profileFunction, dateUtil, profileUtil, triggerDeps)
    fun actionStopProcessing() = ActionStopProcessing(aapsLogger, rh, pumpEnactResultProvider)
    fun actionStopTempTarget() = ActionStopTempTarget(aapsLogger, rh, pumpEnactResultProvider, persistenceLayer, dateUtil)

    /**
     * A new, empty action of the given type. The name may be plain or fully qualified, because that is
     * how it is written in stored automations. Returns null on an unknown type.
     */
    fun instantiate(className: String): Action? =
        when (className.substringAfterLast('.')) {
            ActionAlarm::class.simpleName                -> actionAlarm()
            ActionSettingsExport::class.simpleName       -> actionSettingsExport()
            ActionCarePortalEvent::class.simpleName      -> actionCarePortalEvent()
            ActionDisableScene::class.simpleName         -> actionDisableScene()
            ActionDummy::class.simpleName                -> actionDummy()
            ActionEnableScene::class.simpleName          -> actionEnableScene()
            ActionSMBChange::class.simpleName            -> actionSMBChange()
            ActionNotification::class.simpleName         -> actionNotification()
            ActionProfileSwitch::class.simpleName        -> actionProfileSwitch()
            ActionProfileSwitchPercent::class.simpleName -> actionProfileSwitchPercent()
            ActionRunAutotune::class.simpleName          -> actionRunAutotune()
            ActionRunScene::class.simpleName             -> actionRunScene()
            ActionSendSMS::class.simpleName              -> actionSendSMS()
            ActionStartTempTarget::class.simpleName      -> actionStartTempTarget()
            ActionStopProcessing::class.simpleName       -> actionStopProcessing()
            ActionStopTempTarget::class.simpleName       -> actionStopTempTarget()
            else                                             -> null
        }

    /**
     * Rebuilds an action from its stored form. Returns null on an unknown or malformed type, which is
     * what the caller sees for an automation saved by a newer version.
     */
    fun instantiate(obj: JsonObject): Action? {
        try {
            val type = obj.lenientString("type")
            val data = obj["data"] as? JsonObject ?: JsonObject(emptyMap())
            val action = instantiate(type) ?: throw IllegalArgumentException("Unknown action type: $type")
            return action.fromJSON(data.toString())
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
    }
}
