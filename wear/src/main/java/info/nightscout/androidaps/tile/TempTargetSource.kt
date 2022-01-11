package info.nightscout.androidaps.tile

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.TempTargetActivity

object TempTargetSource : TileSource {

    override fun getActions(): List<Action> {
        return listOf(
            Action(
                id = 0,
                settingName = "activity",
                nameRes = R.string.temp_target_activity,
                iconRes = R.drawable.ic_target_activity,
                activityClass = TempTargetActivity::class.java.getName(),
                background = true,
                actionString = "temptarget false 90 8.0 8.0",
                // actionString = "temptarget preset activity",
            ),
            Action(
                id = 1,
                settingName = "eating_soon",
                nameRes = R.string.temp_target_eating_soon,
                iconRes = R.drawable.ic_target_eatingsoon,
                activityClass = TempTargetActivity::class.java.getName(),
                background = true,
                actionString = "temptarget false 45 4.5 4.5",
                //actionString = "temptarget preset eating",
            ),
            Action(
                id = 2,
                settingName = "hypo",
                nameRes = R.string.temp_target_hypo,
                iconRes = R.drawable.ic_target_hypo,
                activityClass = TempTargetActivity::class.java.getName(),
                background = true,
                actionString = "temptarget false 45 7.0 7.0",
                // actionString = "temptarget preset hypo",
            ),
            Action(
                id = 3,
                settingName = "manual",
                nameRes = R.string.temp_target_manual,
                iconRes = R.drawable.ic_target_manual,
                activityClass = TempTargetActivity::class.java.getName(),
                actionString = "",
                background = false,
            ),
            Action(
                id = 4,
                settingName = "cancel",
                nameRes = R.string.generic_cancel,
                iconRes = R.drawable.ic_target_cancel,
                activityClass = TempTargetActivity::class.java.getName(),
                actionString = "temptarget cancel",
                background = false,
            )
        )
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
