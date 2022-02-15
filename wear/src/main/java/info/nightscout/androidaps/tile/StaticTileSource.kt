package info.nightscout.androidaps.tile

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.preference.PreferenceManager

class StaticAction(
    val settingName: String,
    buttonText: String,
    buttonTextSub: String? = null,
    activityClass: String,
    @DrawableRes iconRes: Int,
    actionString: String? = null,
    message: String? = null,
) : Action(buttonText, buttonTextSub, activityClass, iconRes, actionString, message)

abstract class StaticTileSource : TileSource {

    abstract fun getActions(resources: Resources): List<StaticAction>

    abstract val preferencePrefix: String
    abstract fun getDefaultConfig(): Map<String, String>

    override fun getSelectedActions(context: Context): List<Action> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        setDefaultSettings(sharedPrefs)

        val actionList: MutableList<Action> = mutableListOf()
        for (i in 1..4) {
            val action = getActionFromPreference(context.resources, sharedPrefs, i)
            if (action != null) {
                actionList.add(action)
            }
        }
        if (actionList.isEmpty()) {
            return getActions(context.resources).take(4)
        }
        return actionList
    }

    override fun getValidFor(context: Context): Long? = null

    private fun getActionFromPreference(resources: Resources, sharedPrefs: SharedPreferences, index: Int): Action? {
        val actionPref = sharedPrefs.getString(preferencePrefix + index, "none")
        return getActions(resources).find { action -> action.settingName == actionPref }
    }

    private fun setDefaultSettings(sharedPrefs: SharedPreferences) {
        val defaults = getDefaultConfig()
        val firstKey = defaults.firstNotNullOf { settings -> settings.key }
        if (!sharedPrefs.contains(firstKey)) {
            val editor = sharedPrefs.edit()
            for ((key, value) in defaults) {
                editor.putString(key, value)
            }
            editor.apply()
        }
    }

}
