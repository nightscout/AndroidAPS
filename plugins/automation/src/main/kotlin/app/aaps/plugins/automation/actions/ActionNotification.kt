package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

class ActionNotification(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    var text = InputString()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.notification
    override fun shortDescription(): String = rh.gs(R.string.notification_message, text.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_notifications

    override suspend fun doAction(callback: Callback) {
        notificationManager.post(NotificationId.AUTOMATION_MESSAGE, text.value)
        appScope.launch {
            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asAnnouncement(text.value),
                timestamp = dateUtil.now(),
                action = app.aaps.core.data.ue.Action.TREATMENT,
                source = Sources.Automation,
                note = text.value,
                listValues = listOf()
            )
        }
        rxBus.send(EventRefreshOverview("ActionNotification"))
        callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
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
            .add(LabelWithElement(rh, rh.gs(R.string.message_short), "", text))
            .build(root)
    }

    override fun isValid(): Boolean = text.value.isNotEmpty()
}