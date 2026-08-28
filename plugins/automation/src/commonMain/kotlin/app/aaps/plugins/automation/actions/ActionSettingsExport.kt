package app.aaps.plugins.automation.actions

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.objects.extensions.asSettingsExport
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.elements.InputString
import dev.zacsweers.metro.Provider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSettingsExport(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val rxBus: RxBus,
    private val notificationManager: NotificationManager,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val importExportPrefs: ImportExportPrefs,
    private val exportPasswordDataStore: ExportPasswordDataStore,
    private val preferences: Preferences
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    private val text = InputString()

    override fun friendlyName(): TextRef = CoreUiStrings.exportsettings
    override fun shortDescription(): String = rh.gs(AutomationStrings.exportsettings_message, text.value)
    override fun composeIcon() = Icons.Filled.FileDownload
    override fun elementType() = ElementType.SETTINGS

    override fun isValid(): Boolean = true

    override suspend fun doAction(): PumpEnactResult {

        // Feedback on result
        var exportResultMessage: String
        var exportResultComment: TextRef        // Comment text set in code
        var exportResultLevel: NotificationLevel // Level for user notification when done
        var announceAlert = false      // Also post an announcement (NS)

        if (exportPasswordDataStore.exportPasswordStoreEnabled()) {

            // Get the (encrypted) password and status from the DataStore
            val (password, isExpired, isAboutToExpire) = exportPasswordDataStore.getPasswordFromDataStore()
            aapsLogger.debug(LTag.AUTOMATION, "Exporting settings: passwordIsNotEmpty=${password.isNotEmpty()}, isExpired=$isExpired, isAboutToExpire=$isAboutToExpire")

            // And do according to password state
            if (password.isNotEmpty() && !isExpired) { // Password is not empty and not isExpired
                // Password is not empty and not expired
                if (isAboutToExpire) {
                    // Password is about to expire and needs re-entering by user soon: notify user
                    // Note: we are allowed to export!
                    exportResultComment = CoreUiStrings.export_warning
                    exportResultMessage = rh.gs(CoreUiStrings.export_result_message_about_to_expire)
                    exportResultLevel = NotificationLevel.LOW  // LOW -> e.g. color ORANGE
                } else {
                    // We have a valid password: start exporting, then notify
                    exportResultComment = CoreUiStrings.export_ok
                    exportResultMessage = rh.gs(CoreUiStrings.export_result_message_exported)
                    exportResultLevel = NotificationLevel.INFO // INFO -> e.g. color GREEN
                }
                // Execute settings export, then notify user
                if (!importExportPrefs.exportSharedPreferencesNonInteractive(password)) {
                    // :-( Export failed (see logfile!?)
                    aapsLogger.error(LTag.AUTOMATION, "ERROR: exportSharedPreferencesNonInteractive() failed to export settings")
                    exportResultComment = CoreUiStrings.export_failed
                    exportResultMessage = rh.gs(CoreUiStrings.export_result_message_failed)
                    exportResultLevel = NotificationLevel.IMPORTANT // URGENT -> e.g. color RED
                    announceAlert = true
                }
            } else {
                // No password or was expired and needs re-entering by user
                exportResultComment = CoreUiStrings.export_expired
                exportResultMessage = rh.gs(CoreUiStrings.export_result_message_expired)
                exportResultLevel = NotificationLevel.IMPORTANT  // URGENT -> e.g. color RED
                // Clear password in datastore, then notify user
                aapsLogger.info(LTag.AUTOMATION, "No password or was expired and needs re-entering by user")
                exportPasswordDataStore.clearPasswordDataStore()
                announceAlert = true
            }
        } else {
            // Not enabled, do nothing and notify user
            exportResultComment = CoreUiStrings.export_disabled
            exportResultMessage = rh.gs(CoreUiStrings.export_result_message_disabled)
            exportResultLevel = NotificationLevel.IMPORTANT
            aapsLogger.info(LTag.AUTOMATION, "Settings export ignored: unattended settings export is disabled")
        }
        // send notification
        notificationManager.post(NotificationId.SETTINGS_EXPORT_RESULT, exportResultMessage, exportResultLevel)

        // Insert therapy event EXPORT_SETTINGS for automation trigger to uniquely detect.
        val error = "${text.value}: $exportResultMessage"
        aapsLogger.debug(LTag.AUTOMATION, "Insert therapy EXPORT_SETTINGS event, error=:${error}, doAlsoAnnouncement=$announceAlert")
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asSettingsExport(error = error),
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.EXPORT_SETTINGS, // Signal export was done to automation!
            source = Sources.Automation,
            note = exportResultMessage,
            listValues = listOf()
        )

        if (announceAlert && preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors) && config.APS) {
            // Do additional event type announcement for aapsClient alerting
            val alert = "${rh.gs(CoreUiStrings.export_alert)}(${text.value}): $exportResultMessage"
            aapsLogger.debug(LTag.AUTOMATION, "Insert therapy ALERT/ANNOUNCEMENT event, error=:${alert}")
            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asAnnouncement(error = alert),
                timestamp = dateUtil.now(),
                action = app.aaps.core.data.ue.Action.EXPORT_SETTINGS,
                source = Sources.Automation,
                note = exportResultMessage,
                listValues = listOf()
            )
        }

        rxBus.send(EventRefreshOverview("ActionSettingsExport"))
        return pumpEnactResultProvider().success(true).comment(exportResultComment)
    }

    override fun toJSON(): String {
        val data = buildJsonObject { put("text", text.value) }
        return buildJsonObject {
            put("type", this@ActionSettingsExport::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        text.value = o.lenientString("text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

}