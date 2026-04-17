package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.plugins.automation.R
import com.google.gson.JsonObject
import dagger.android.HasAndroidInjector
import org.json.JSONObject

class ActionStopProcessing(injector: HasAndroidInjector) : Action(injector) {

    override fun friendlyName(): Int = R.string.stop_processing
    override fun shortDescription(): String = rh.gs(R.string.stop_processing)
    override fun composeIcon() = Icons.Filled.Stop

    override fun isValid(): Boolean = true

    override suspend fun doAction(): PumpEnactResult {
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
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