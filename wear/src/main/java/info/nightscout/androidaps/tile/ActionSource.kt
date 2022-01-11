package info.nightscout.androidaps.tile

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.TreatmentActivity
import info.nightscout.androidaps.interaction.actions.ECarbActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity
import info.nightscout.androidaps.interaction.actions.WizardActivity

object ActionSource : TileSource {

    override fun getActions(): List<Action> {
        return listOf(
            Action(
                id = 0,
                settingName = "wizzard",
                nameRes = R.string.menu_wizard,
                iconRes = R.drawable.ic_calculator_green,
                activityClass = WizardActivity::class.java.getName(),
                background = false,
                actionString = "",
            ),
            Action(
                id = 1,
                settingName = "bolus",
                nameRes = R.string.action_bolus,
                iconRes = R.drawable.ic_bolus_carbs,
                activityClass = TreatmentActivity::class.java.getName(),
                background = false,
                actionString = "",
            ),
            Action(
                id = 2,
                settingName = "carbs",
                nameRes = R.string.action_carbs,
                iconRes = R.drawable.ic_carbs_orange,
                activityClass = ECarbActivity::class.java.getName(),
                background = false,
                actionString = "",
            ),
            Action(
                id = 3,
                settingName = "temp_target",
                nameRes = R.string.menu_tempt,
                iconRes = R.drawable.ic_temptarget_flat,
                activityClass = TempTargetActivity::class.java.getName(),
                background = false,
                actionString = "",
            )
        )
    }

    override fun getDefaultConfig(): Map<String, String> {
        return mapOf(
            "tile_action_1" to "wizzard",
            "tile_action_2" to "bolus",
            "tile_action_3" to "carbs",
            "tile_action_4" to "temp_target"
        )
    }

}
