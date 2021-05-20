package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationUserMessage
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class ActionNotification(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    var text = InputString()

    override fun friendlyName(): Int = R.string.notification
    override fun shortDescription(): String = resourceHelper.gs(R.string.notification_message, text.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_notifications

    override fun doAction(callback: Callback) {
        val notification = NotificationUserMessage(text.value)
        rxBus.send(EventNewNotification(notification))
        disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(text.value)).subscribe()
        rxBus.send(EventRefreshOverview("ActionNotification"))
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
    }

    override fun toJSON(): String {
        val data = JSONObject().put("text", text.value)
        return JSONObject()
            .put("type", this.javaClass.name)
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
            .add(LabelWithElement(resourceHelper, resourceHelper.gs(R.string.message_short), "", text))
            .build(root)
    }

    override fun isValid(): Boolean = text.value.isNotEmpty()
}