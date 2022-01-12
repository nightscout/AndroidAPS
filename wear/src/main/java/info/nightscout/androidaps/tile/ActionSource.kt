package info.nightscout.androidaps.tile

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.BolusActivity
import info.nightscout.androidaps.interaction.actions.TreatmentActivity
import info.nightscout.androidaps.interaction.actions.ECarbActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity
import info.nightscout.androidaps.interaction.actions.WizardActivity

object ActionSource : TileSource {

    override fun getActions(): List<Action> {
        return listOf(
            Action(
                id = 0,
                settingName = "wizard",
                nameRes = R.string.menu_wizard_short,
                iconRes = R.drawable.ic_calculator_green,
                activityClass = WizardActivity::class.java.name,
            ),
            Action(
                id = 1,
                settingName = "treatment",
                nameRes = R.string.menu_treatment_short,
                iconRes = R.drawable.ic_bolus_carbs,
                activityClass = TreatmentActivity::class.java.name,
            ),
            Action(
                id = 2,
                settingName = "bolus",
                nameRes = R.string.action_insulin,
                iconRes = R.drawable.ic_bolus,
                activityClass = BolusActivity::class.java.name,
            ),
            Action(
                id = 3,
                settingName = "carbs",
                nameRes = R.string.action_carbs,
                iconRes = R.drawable.ic_carbs_orange,
                activityClass = ECarbActivity::class.java.name,
            ),
            Action(
                id = 4,
                settingName = "temp_target",
                nameRes = R.string.menu_tempt,
                iconRes = R.drawable.ic_temptarget_flat,
                activityClass = TempTargetActivity::class.java.name,
            )
        )
    }

    override fun getDefaultConfig(): Map<String, String> {
        return mapOf(
            "tile_action_1" to "wizard",
            "tile_action_2" to "treatment",
            "tile_action_3" to "carbs",
            "tile_action_4" to "temp_target"
        )
    }

}
