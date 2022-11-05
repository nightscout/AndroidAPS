package info.nightscout.automation.actions

import androidx.annotation.DrawableRes
import com.google.gson.JsonObject
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.queue.Callback
import info.nightscout.automation.R
import org.json.JSONObject

class ActionStopProcessing(injector: HasAndroidInjector) : Action(injector) {

    override fun friendlyName(): Int = R.string.stop_processing
    override fun shortDescription(): String = rh.gs(R.string.stop_processing)
    @DrawableRes override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun isValid(): Boolean = true

    override fun doAction(callback: Callback) {
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok)).run()
    }

    override fun toJSON(): String {
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", JsonObject())
            .toString()
    }

    override fun fromJSON(data: String): Action = this

    override fun hasDialog(): Boolean = false
}