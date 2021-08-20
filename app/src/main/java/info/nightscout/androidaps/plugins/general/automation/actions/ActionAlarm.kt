package info.nightscout.androidaps.plugins.general.automation.actions

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationUserMessage
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.alertDialogs.WarningDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionAlarm(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var context: Context

    var text = InputString(injector)

    constructor(injector: HasAndroidInjector, text: String) : this(injector) {
        this.text = InputString(injector, text)
    }
    override fun friendlyName(): Int = R.string.alarm
    override fun shortDescription(): String = resourceHelper.gs(R.string.alarm_message, text.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_access_alarm_24dp

    override fun doAction(callback: Callback) {
        val i = Intent(context, ErrorHelperActivity::class.java)
        i.putExtra("soundid", R.raw.modern_alarm)
        i.putExtra("status", text.value)
        i.putExtra("title", resourceHelper.gs(R.string.alarm))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)

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
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.alarm_short), "", text))
            .build(root)
    }
}