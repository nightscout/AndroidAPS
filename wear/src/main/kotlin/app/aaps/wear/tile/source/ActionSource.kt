package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.BolusActivity
import app.aaps.wear.interaction.actions.CarbActivity
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.WizardActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionSource @Inject constructor(context: Context, sp: SP, aapsLogger: AAPSLogger) : StaticTileSource(context, sp, aapsLogger) {

    override val preferencePrefix = "tile_action_"

    override fun getActions(resources: Resources): List<StaticAction> {
        return listOf(
            StaticAction(
                settingName = "wizard",
                buttonText = resources.getString(R.string.menu_wizard_short),
                iconRes = R.drawable.ic_calculator,
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
            ),
            StaticAction(
                settingName = "profile_switch",
                buttonText = resources.getString(R.string.status_profile_switch),
                iconRes = R.drawable.ic_profile_switch,
                activityClass = BackgroundActionActivity::class.java.name,
                action = EventData.ActionProfileSwitchSendInitialData(System.currentTimeMillis())
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
