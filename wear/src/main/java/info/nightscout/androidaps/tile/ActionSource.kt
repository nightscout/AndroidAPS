package info.nightscout.androidaps.tile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.BolusActivity
import info.nightscout.androidaps.interaction.actions.ECarbActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity
import info.nightscout.androidaps.interaction.actions.WizardActivity

data class Action(
    val id: Long,
    @StringRes val nameRes: Int,
    val activityClass: String,
    @DrawableRes val iconRes: Int,
)

object ActionSource {

    fun getActions(): List<Action> {
        return listOf(
            Action(
                id = 0,
                nameRes = R.string.menu_wizard,
                iconRes = R.drawable.ic_calculator_green,
                activityClass = WizardActivity::class.java.getName(),
            ),
            Action(
                id = 1,
                nameRes = R.string.action_bolus,
                iconRes = R.drawable.ic_bolus_carbs,
                activityClass = BolusActivity::class.java.getName(),
            ),
            Action(
                id = 2,
                nameRes = R.string.action_carbs,
                iconRes = R.drawable.ic_carbs_orange,
                activityClass = ECarbActivity::class.java.getName(),
            ),
            Action(
                id = 3,
                nameRes = R.string.menu_tempt,
                iconRes = R.drawable.ic_temptarget_flat,
                activityClass = TempTargetActivity::class.java.getName(),
            )
        )
    }
}
