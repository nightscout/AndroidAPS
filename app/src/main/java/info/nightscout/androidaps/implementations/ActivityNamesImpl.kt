package info.nightscout.androidaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.fragment.app.FragmentManager
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.dialogs.WizardDialog
import info.nightscout.androidaps.interfaces.ActivityNames
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.ui.activities.BolusProgressHelperActivity
import info.nightscout.ui.activities.ErrorHelperActivity
import info.nightscout.ui.activities.TDDStatsActivity
import javax.inject.Inject

class ActivityNamesImpl @Inject constructor() : ActivityNames {

    override val mainActivityClass: Class<*>
        get() = MainActivity::class.java

    override val tddStatsActivity: Class<*>
        get() = TDDStatsActivity::class.java

    override val errorHelperActivity: Class<*>
        get() = ErrorHelperActivity::class.java

    override val bolusProgressHelperActivity: Class<*>
        get() = BolusProgressHelperActivity::class.java

    override fun runAlarm(ctx: Context, status: String, title: String, @RawRes soundId: Int) {
        val i = Intent(ctx, errorHelperActivity)
        i.putExtra(AlarmSoundService.SOUND_ID, soundId)
        i.putExtra(AlarmSoundService.STATUS, status)
        i.putExtra(AlarmSoundService.TITLE, title)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    override fun runWizard(fragmentManager: FragmentManager, carbs: Int, name: String) {
        WizardDialog().also { dialog ->
            dialog.arguments = Bundle().also { bundle ->
                bundle.putDouble("carbs_input", carbs.toDouble())
                bundle.putString("notes_input", " $name - ${carbs}g")
            }
        }.show(fragmentManager, "Food Item")

    }
}