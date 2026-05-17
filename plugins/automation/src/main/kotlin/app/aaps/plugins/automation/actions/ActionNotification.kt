package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputString
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionNotification(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil

    var text = InputString()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.notification
    override fun shortDescription(): String = rh.gs(R.string.notification_message, text.value)
    override fun composeIcon() = Icons.Filled.Notifications
    override fun composeIconTint() = IconTint.Announce

    override suspend fun doAction(): PumpEnactResult {
        notificationManager.post(NotificationId.AUTOMATION_MESSAGE, text.value)
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asAnnouncement(text.value),
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.TREATMENT,
            source = Sources.Automation,
            note = text.value,
            listValues = listOf()
        )
        rxBus.send(EventRefreshOverview("ActionNotification"))
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
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

    override fun isValid(): Boolean = text.value.isNotEmpty()
}