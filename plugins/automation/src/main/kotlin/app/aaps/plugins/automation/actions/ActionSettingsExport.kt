package app.aaps.plugins.automation.actions

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.notifications.NotificationUserMessage
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.objects.extensions.asSettingsExport
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class ActionSettingsExport(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var preferences: Preferences

    private val disposable = CompositeDisposable()
    private val text = InputString()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.exportsettings
    override fun shortDescription(): String = rh.gs(R.string.exportsettings_message, text.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.objects.R.drawable.ic_export_settings_24dp

    override fun isValid(): Boolean = true

    override fun doAction(callback: Callback) {

        // Feedback on result
        var exportResultMessage: String
        var exportResultComment: Int        // Comment string ID set in code
        var notification: Notification      // Send user notification when done
        var announceAlert = false      // Also post an announcement (NS)

        if (exportPasswordDataStore.exportPasswordStoreEnabled()) {

            // Get the (encrypted) password and status from the DataStore
            val (password, isExpired, isAboutToExpire) = exportPasswordDataStore.getPasswordFromDataStore(context)
            aapsLogger.debug(LTag.AUTOMATION, "Exporting settings: passwordIsNotEmpty=${password.isNotEmpty()}, isExpired=$isExpired, isAboutToExpire=$isAboutToExpire")

            // And do according to password state
            if (password.isNotEmpty() && !isExpired) { // Password is not empty and not isExpired
                // Password is not empty and not expired
                if (isAboutToExpire) {
                    // Password is about to expire and needs re-entering by user soon: notify user
                    // Note: we are allowed to export!
                    exportResultComment = app.aaps.core.ui.R.string.export_warning
                    exportResultMessage = rh.gs(app.aaps.core.ui.R.string.export_result_message_about_to_expire)
                    notification = NotificationUserMessage(exportResultMessage, Notification.LOW)  // LOW -> e.g. color ORANGE
                } else {
                    // We have a valid password: start exporting, then notify
                    exportResultComment = app.aaps.core.ui.R.string.export_ok
                    exportResultMessage = rh.gs(app.aaps.core.ui.R.string.export_result_message_exported)
                    notification = NotificationUserMessage(exportResultMessage, Notification.INFO) // INFO -> e.g. color GREEN
                }
                // Execute settings export, then notify user
                if (!importExportPrefs.exportSharedPreferencesNonInteractive(context, password)) {
                    // :-( Export failed (see logfile!?)
                    aapsLogger.error(LTag.AUTOMATION, "ERROR: exportSharedPreferencesNonInteractive() failed to export settings")
                    exportResultComment = app.aaps.core.ui.R.string.export_failed
                    exportResultMessage = rh.gs(app.aaps.core.ui.R.string.export_result_message_failed)
                    notification = NotificationUserMessage(exportResultMessage, Notification.URGENT) // URGENT -> e.g. color RED
                    announceAlert = true
                }
            } else {
                // No password or was expired and needs re-entering by user
                exportResultComment = app.aaps.core.ui.R.string.export_expired
                exportResultMessage = rh.gs(app.aaps.core.ui.R.string.export_result_message_expired)
                notification = NotificationUserMessage(exportResultMessage, Notification.URGENT)  // URGENT -> e.g. color RED
                // Clear password in datastore, then notify user
                aapsLogger.info(LTag.AUTOMATION, "No password or was expired and needs re-entering by user")
                exportPasswordDataStore.clearPasswordDataStore(context)
                announceAlert = true
            }
        } else {
            // Not enabled, do nothing and notify user
            exportResultComment = app.aaps.core.ui.R.string.export_disabled
            exportResultMessage = rh.gs(app.aaps.core.ui.R.string.export_result_message_disabled)
            notification = NotificationUserMessage(exportResultMessage, Notification.URGENT)
            aapsLogger.info(LTag.AUTOMATION, "Settings export ignored: unattended settings export is disabled")
        }
        // send notification
        rxBus.send(EventNewNotification(notification))

        // Insert therapy event EXPORT_SETTINGS for automation trigger to uniquely detect.
        val error = "${text.value}: $exportResultMessage"
        aapsLogger.debug(LTag.AUTOMATION, "Insert therapy EXPORT_SETTINGS event, error=:${error}, doAlsoAnnouncement=$announceAlert")
        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asSettingsExport(error = error),
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.EXPORT_SETTINGS, // Signal export was done to automation!
            source = Sources.Automation,
            note = exportResultMessage,
            listValues = listOf()
        ).subscribe()

        if (announceAlert && preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors) && config.APS) {
            // Do additional event type announcement for aapsClient alerting
            val alert = "${rh.gs(app.aaps.core.ui.R.string.export_alert)}(${text.value}): $exportResultMessage"
            aapsLogger.debug(LTag.AUTOMATION, "Insert therapy ALERT/ANNOUNCEMENT event, error=:${alert}")
            disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asAnnouncement(error = alert),
                timestamp = dateUtil.now(),
                action = app.aaps.core.data.ue.Action.EXPORT_SETTINGS,
                source = Sources.Automation,
                note = exportResultMessage,
                listValues = listOf()
            ).subscribe()
        }

        rxBus.send(EventRefreshOverview("ActionSettingsExport"))
        callback.result(pumpEnactResultProvider.get().success(true).comment(exportResultComment)).run()
    }

    override fun toJSON(): String {
        val data = JSONObject().put("text", text.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        text.value = JsonHelper.safeGetString(o, "text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.export_settings_short), "", text))
            .build(root)
    }
}