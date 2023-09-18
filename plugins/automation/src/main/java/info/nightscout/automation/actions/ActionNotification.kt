package info.nightscout.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.core.events.EventNewNotification
import info.nightscout.core.utils.JsonHelper
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.interfaces.notifications.NotificationUserMessage
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventRefreshOverview
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class ActionNotification(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    var text = InputString()

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.notification
    override fun shortDescription(): String = rh.gs(R.string.notification_message, text.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_notifications

    override fun doAction(callback: Callback) {
        val notification = NotificationUserMessage(text.value)
        rxBus.send(EventNewNotification(notification))
        disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(text.value)).subscribe()
        rxBus.send(EventRefreshOverview("ActionNotification"))
        callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
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