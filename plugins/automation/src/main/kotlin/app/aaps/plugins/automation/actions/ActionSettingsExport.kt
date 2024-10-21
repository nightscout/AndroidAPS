package app.aaps.plugins.automation.actions

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.notifications.NotificationUserMessage
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
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

    private val disposable = CompositeDisposable()
    private val text = InputString()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.exportsettings
    override fun shortDescription(): String = rh.gs(R.string.exportsettings_message, text.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.objects.R.drawable.ic_export_settings_24dp

    override fun isValid(): Boolean = true

    override fun doAction(callback: Callback) {
        val message: String

        // Feedback on result
        var exportResultComment: Int        // Comment string ID set in code

        if (exportPasswordDataStore.exportPasswordStoreEnabled()) {
            // Send user notification when done
            val notification: Notification

            // Get the (encrypted) password and status from the DataStore
            val (password, isExpired, isAboutToExpire) = exportPasswordDataStore.getPasswordFromDataStore(context)
            // An do according to password state
            if (password.isNotEmpty() && !isExpired) { // Password is not empty and not isExpired
                // Password is not empty and not expired
                if (isAboutToExpire) {
                    // Password is about to expire and needs re-entering by user soon: notify user
                    // Note: we are allowed to export!
                    message = "Settings exported: password about to expire soon. Export manually and (re)enter password."
                    notification = NotificationUserMessage(message, Notification.LOW)  // LOW -> e.g. color ORANGE
                    exportResultComment = app.aaps.core.ui.R.string.export_warning
                } else {
                    // We have a valid password: start exporting, then notify
                    message = "Settings exported"
                    notification = NotificationUserMessage(message, Notification.INFO) // INFO -> e.g. color GREEN
                    exportResultComment = app.aaps.core.ui.R.string.export_ok
                }
                // Execute settings export, then notify user
                if (!importExportPrefs.exportSharedPreferencesNonInteractive(context, password)) {
                    exportResultComment = app.aaps.core.ui.R.string.export_failed
                }

            }
            else {
                // No password or was expired and needs re-entering by user
                message = "Settings export canceled: password expired. Export manually and (re)enter!"
                notification = NotificationUserMessage(message, Notification.URGENT)  // Urgent -> e.g. color RED
                exportResultComment = app.aaps.core.ui.R.string.export_expired
                // Clear password in datastore, then notify user
                exportPasswordDataStore.clearPasswordDataStore(context)
            }
            // send notification
            rxBus.send(EventNewNotification(notification))
        }
        else {
            // Not enabled, do nothing and notify user
            message = "Warning (automation): Settings export not enabled!"
            exportResultComment = app.aaps.core.ui.R.string.export_disabled
            val notification = NotificationUserMessage(message, Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        }

        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            // therapyEvent = TE.asAnnouncement(text.value),
            therapyEvent = TE.asSettingsExport(text.value),
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.EXPORT_SETTINGS,
            source = Sources.Automation,
            note = message,
            listValues = listOf()
        ).subscribe()
        rxBus.send(EventRefreshOverview("ActionSettingsExport"))
        callback.result(instantiator.providePumpEnactResult().success(true).comment(exportResultComment)).run()
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