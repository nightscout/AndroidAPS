package app.aaps.plugins.automation.actions

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.notifications.NotificationInfoMessage
import app.aaps.core.interfaces.notifications.NotificationUserMessage
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.ui.TimerUtil
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class ActionSettingsExport(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var timerUtil: TimerUtil
    @Inject lateinit var config: Config
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore

    private val disposable = CompositeDisposable()

    var text = InputString()

    constructor(injector: HasAndroidInjector, text: String) : this(injector) {
        this.text = InputString(text)
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.exportsettings
    override fun shortDescription(): String = rh.gs(R.string.exportsettings_message, text.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.objects.R.drawable.ic_export_settings_24dp

    override fun isValid(): Boolean = true

    override fun doAction(callback: Callback) {
        val message: String

        if (exportPasswordDataStore.exportPasswordStoreEnabled()) {
            val storedPassword = exportPasswordDataStore.getPasswordFromDataStore(context)
            if (storedPassword.first) {
                // We have a password: start exporting & notify info
                importExportPrefs.exportSharedPreferencesNonInteractive(context, storedPassword.second)
                message = "Settings exported"
                val notification = NotificationInfoMessage(message)
                rxBus.send(EventNewNotification(notification))
            } else {
                // No password, was expired and needs re-entering by user, notify user
                exportPasswordDataStore.clearPasswordDataStore(context)
                message = "Settings export canceled: Export manually and (re)enter password!"
                val notification = NotificationUserMessage(message)
                rxBus.send(EventNewNotification(notification))
            }
        }
        else {
            message = "Warning (automation): Settings export not enabled!"
            val notification = NotificationUserMessage(message)
            rxBus.send(EventNewNotification(notification))
        }

        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asAnnouncement(text.value),
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.EXPORT_SETTINGS,
            source = Sources.Automation,
            note = message,
            listValues = listOf()
        ).subscribe()
        rxBus.send(EventRefreshOverview("ActionSettingsExport"))
        callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()

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