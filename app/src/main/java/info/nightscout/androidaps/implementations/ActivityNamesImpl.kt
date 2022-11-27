package info.nightscout.androidaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.HistoryBrowseActivity
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.services.AlarmSoundService
import info.nightscout.configuration.activities.SingleFragmentActivity
import info.nightscout.core.events.EventNewNotification
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.ui.ActivityNames
import info.nightscout.rx.bus.RxBus
import info.nightscout.ui.activities.BolusProgressHelperActivity
import info.nightscout.ui.activities.ErrorHelperActivity
import info.nightscout.ui.activities.TDDStatsActivity
import info.nightscout.ui.dialogs.BolusProgressDialog
import info.nightscout.ui.dialogs.CalibrationDialog
import info.nightscout.ui.dialogs.CarbsDialog
import info.nightscout.ui.dialogs.CareDialog
import info.nightscout.ui.dialogs.ExtendedBolusDialog
import info.nightscout.ui.dialogs.FillDialog
import info.nightscout.ui.dialogs.InsulinDialog
import info.nightscout.ui.dialogs.LoopDialog
import info.nightscout.ui.dialogs.ProfileSwitchDialog
import info.nightscout.ui.dialogs.ProfileViewerDialog
import info.nightscout.ui.dialogs.TempBasalDialog
import info.nightscout.ui.dialogs.TempTargetDialog
import info.nightscout.ui.dialogs.TreatmentDialog
import info.nightscout.ui.dialogs.WizardDialog
import javax.inject.Inject

class ActivityNamesImpl @Inject constructor(
    private val rxBus: RxBus
) : ActivityNames {

    override val mainActivity: Class<*> = MainActivity::class.java
    override val tddStatsActivity: Class<*> = TDDStatsActivity::class.java
    override val historyBrowseActivity: Class<*> = HistoryBrowseActivity::class.java
    override val errorHelperActivity: Class<*> = ErrorHelperActivity::class.java
    override val bolusProgressHelperActivity: Class<*> = BolusProgressHelperActivity::class.java
    override val singleFragmentActivity: Class<*> = SingleFragmentActivity::class.java
    override val preferencesActivity: Class<*> = PreferencesActivity::class.java
    override val myPreferenceFragment: Class<*> = MyPreferenceFragment::class.java
    override val prefGeneral: Int = R.xml.pref_general

    override fun runAlarm(ctx: Context, status: String, title: String, @RawRes soundId: Int) {
        val i = Intent(ctx, errorHelperActivity)
        i.putExtra(AlarmSoundService.SOUND_ID, soundId)
        i.putExtra(AlarmSoundService.STATUS, status)
        i.putExtra(AlarmSoundService.TITLE, title)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    override fun runWizardDialog(fragmentManager: FragmentManager, carbs: Int?, name: String?) {
        WizardDialog().also { dialog ->
            dialog.arguments = Bundle().also { bundle ->
                carbs?.let { bundle.putDouble("carbs_input", carbs.toDouble())}
                name?.let {bundle.putString("notes_input", " $name - ${carbs}g") }
            }
        }.show(fragmentManager, "Food Item")

    }

    override fun runLoopDialog(fragmentManager: FragmentManager, showOkCancel: Int) {
        LoopDialog()
            .also { it.arguments = Bundle().also { bundle -> bundle.putInt("showOkCancel", showOkCancel) } }
            .show(fragmentManager, "LoopDialog")
    }

    override fun runProfileSwitchDialog(fragmentManager: FragmentManager, profileName: String?) {
        ProfileSwitchDialog()
            .also { it.arguments = Bundle().also { bundle -> bundle.putString("profileName", profileName) } }
            .show(fragmentManager, "ProfileSwitchDialog")
    }

    override fun runTempBasalDialog(fragmentManager: FragmentManager) {
        TempBasalDialog()
            .show(fragmentManager, "TempBasalDialog")
    }

    override fun runTreatmentDialog(fragmentManager: FragmentManager) {
        TreatmentDialog()
            .show(fragmentManager, "TreatmentDialog")
    }

    override fun runInsulinDialog(fragmentManager: FragmentManager) {
        InsulinDialog()
            .show(fragmentManager, "InsulinDialog")
    }

    override fun runCalibrationDialog(fragmentManager: FragmentManager) {
        CalibrationDialog()
            .show(fragmentManager, "CalibrationDialog")
    }

    override fun runCarbsDialog(fragmentManager: FragmentManager) {
        CarbsDialog()
            .show(fragmentManager, "CarbsDialog")
    }

    override fun runTempTargetDialog(fragmentManager: FragmentManager) {
        TempTargetDialog()
            .show(fragmentManager, "TempTargetDialog")
    }

    override fun runExtendedBolusDialog(fragmentManager: FragmentManager) {
        ExtendedBolusDialog()
            .show(fragmentManager, "ExtendedBolusDialog")
    }

    override fun runFillDialog(fragmentManager: FragmentManager) {
        FillDialog()
            .show(fragmentManager, "FillDialog")
    }

    override fun runProfileViewerDialog(fragmentManager: FragmentManager, time: Long, mode: ActivityNames.Mode, customProfile: String?, customProfileName: String?, customProfile2: String?) {
        ProfileViewerDialog()
            .also {
                it.arguments = Bundle().also { bundle ->
                    bundle.putLong("time", time)
                    bundle.putInt("mode", mode.ordinal)
                    bundle.putString("customProfile", customProfile)
                    bundle.putString("customProfileName", customProfileName)
                    bundle.putString("customProfile2", customProfile2)
                }
            }
            .show(fragmentManager, "ProfileViewer")
    }

    override fun runCareDialog(fragmentManager: FragmentManager, options: ActivityNames.EventType, @StringRes event: Int) {
        CareDialog()
            .also {
                it.arguments = Bundle().also { bundle ->
                    bundle.putInt("event", event)
                    bundle.putInt("options", options.ordinal)
                }
            }
            .show(fragmentManager, "CareDialog")
    }
    override fun runBolusProgressDialog(fragmentManager: FragmentManager, insulin: Double, id: Long) {
        BolusProgressDialog().also {
            it.setInsulin(insulin)
            it.setId(id)
            it.show(fragmentManager, "BolusProgress")
        }
    }

    override fun addNotification(id: Int, text: String, level: Int) {
        rxBus.send(EventNewNotification(Notification(id, text, level)))
    }

    override fun addNotificationValidFor(id: Int, text: String, level: Int, validMinutes: Int) {
        rxBus.send(EventNewNotification(Notification(id, text, level, validMinutes)))
    }

    override fun addNotificationWithSound(id: Int, text: String, level: Int, soundId: Int) {
        rxBus.send(EventNewNotification(Notification(id, text, level).also { it.soundId = soundId }))
    }

    override fun addNotificationValidTo(id: Int, date: Long, text: String, level: Int, validTo: Long) {
        rxBus.send(EventNewNotification(Notification(id, System.currentTimeMillis(), text, level,validTo)))
    }
}