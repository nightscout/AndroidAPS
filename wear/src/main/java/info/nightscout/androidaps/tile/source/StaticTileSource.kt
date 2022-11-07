package info.nightscout.androidaps.tile.source

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DrawableRes
import info.nightscout.androidaps.tile.Action
import info.nightscout.androidaps.tile.TileSource
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.sharedPreferences.SP

abstract class StaticTileSource(val context: Context, val sp: SP, val aapsLogger: AAPSLogger) : TileSource {

    class StaticAction(
        val settingName: String,
        buttonText: String,
        buttonTextSub: String? = null,
        activityClass: String,
        @DrawableRes iconRes: Int,
        action: EventData? = null,
        message: String? = null,
    ) : Action(buttonText, buttonTextSub, activityClass, iconRes, action, message)

    abstract fun getActions(resources: Resources): List<StaticAction>

    abstract val preferencePrefix: String
    abstract fun getDefaultConfig(): Map<String, String>

    override fun getSelectedActions(): List<Action> {
        setDefaultSettings()

        val actionList: MutableList<Action> = mutableListOf()
        for (i in 1..4) {
            val action = getActionFromPreference(i)
            if (action != null) {
                actionList.add(action)
            }
        }
        if (actionList.isEmpty()) {
            return getActions(context.resources).take(4)
        }
        return actionList
    }

    override fun getValidFor(): Long? = null

    private fun getActionFromPreference(index: Int): Action? {
        val actionPref = sp.getString(preferencePrefix + index, "none")
        return getActions(context.resources).find { action -> action.settingName == actionPref }
    }

    private fun setDefaultSettings() {
        val defaults = getDefaultConfig()
        val firstKey = defaults.firstNotNullOf { settings -> settings.key }
        if (!sp.contains(firstKey)) {
            for ((key, value) in defaults) {
                sp.putString(key, value)
            }
        }
    }
}
