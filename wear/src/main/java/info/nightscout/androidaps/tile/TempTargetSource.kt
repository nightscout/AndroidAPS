package info.nightscout.androidaps.tile

import android.content.res.Resources
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.BackgroundActionActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity

object TempTargetSource : StaticTileSource() {

    override val preferencePrefix = "tile_tempt_"

    override fun getActions(resources: Resources): List<StaticAction> {
        val message = resources.getString(R.string.action_tempt_confirmation)
        return listOf(
            StaticAction(
                settingName = "activity",
                buttonText = resources.getString(R.string.temp_target_activity),
                iconRes = R.drawable.ic_target_activity,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                // actionString = "temptarget false 90 8.0 8.0",
                actionString = "temptarget preset activity",
            ),
            StaticAction(
                settingName = "eating_soon",
                buttonText = resources.getString(R.string.temp_target_eating_soon),
                iconRes = R.drawable.ic_target_eatingsoon,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                // actionString = "temptarget false 45 4.5 4.5",
                actionString = "temptarget preset eating",
            ),
            StaticAction(
                settingName = "hypo",
                buttonText = resources.getString(R.string.temp_target_hypo),
                iconRes = R.drawable.ic_target_hypo,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                // actionString = "temptarget false 45 7.0 7.0",
                actionString = "temptarget preset hypo",
            ),
            StaticAction(
                settingName = "manual",
                buttonText = resources.getString(R.string.temp_target_manual),
                iconRes = R.drawable.ic_target_manual,
                activityClass = TempTargetActivity::class.java.name,
            ),
            StaticAction(
                settingName = "cancel",
                buttonText = resources.getString(R.string.generic_cancel),
                iconRes = R.drawable.ic_target_cancel,
                activityClass = BackgroundActionActivity::class.java.name,
                message = message,
                actionString = "temptarget cancel",
            )
        )
    }

    override fun getResourceReferences(resources: Resources): List<Int> {
        return getActions(resources).map { it.iconRes }
    }

    override fun getDefaultConfig(): Map<String, String> {
        return mapOf(
            "tile_tempt_1" to "activity",
            "tile_tempt_2" to "eating_soon",
            "tile_tempt_3" to "hypo",
            "tile_tempt_4" to "manual"
        )
    }
}
