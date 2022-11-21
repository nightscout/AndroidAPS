package info.nightscout.androidaps.tile.source

import android.content.Context
import android.content.res.Resources
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.*
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionSource @Inject constructor(context: Context, sp : SP, aapsLogger: AAPSLogger) : StaticTileSource(context, sp, aapsLogger) {

    override val preferencePrefix = "tile_action_"

    override fun getActions(resources: Resources): List<StaticAction> {
        return listOf(
            StaticAction(
                settingName = "wizard",
                buttonText = resources.getString(R.string.menu_wizard_short),
                iconRes = R.drawable.ic_calculator_green,
                activityClass = WizardActivity::class.java.name,
            ),
            StaticAction(
                settingName = "treatment",
                buttonText = resources.getString(R.string.menu_treatment_short),
                iconRes = R.drawable.ic_bolus_carbs,
                activityClass = TreatmentActivity::class.java.name,
            ),
            StaticAction(
                settingName = "bolus",
                buttonText = resources.getString(R.string.action_insulin),
                iconRes = R.drawable.ic_bolus,
                activityClass = BolusActivity::class.java.name,
            ),
            StaticAction(
                settingName = "carbs",
                buttonText = resources.getString(R.string.action_carbs),
                iconRes = R.drawable.ic_carbs_orange,
                activityClass = CarbActivity::class.java.name,
            ),
            StaticAction(
                settingName = "ecarbs",
                buttonText = resources.getString(R.string.action_ecarbs),
                iconRes = R.drawable.ic_carbs_orange,
                activityClass = ECarbActivity::class.java.name,
            ),
            StaticAction(
                settingName = "temp_target",
                buttonText = resources.getString(R.string.menu_tempt),
                iconRes = R.drawable.ic_temptarget_flat,
                activityClass = TempTargetActivity::class.java.name,
            )
        )
    }

    override fun getResourceReferences(resources: Resources): List<Int> {
        return getActions(resources).map { it.iconRes }
    }

    override fun getDefaultConfig(): Map<String, String> {
        return mapOf(
            "tile_action_1" to "wizard",
            "tile_action_2" to "treatment",
            "tile_action_3" to "ecarbs",
            "tile_action_4" to "temp_target"
        )
    }

}
