package app.aaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import app.aaps.MainActivity
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.MyPreferenceFragment
import app.aaps.activities.PreferencesActivity
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.toJson
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.ui.activities.BolusProgressHelperActivity
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.activities.ProfileViewerActivity
import app.aaps.ui.activities.QuickWizardListActivity
import app.aaps.ui.activities.TDDStatsActivity
import app.aaps.ui.dialogs.AlertDialogs
import app.aaps.ui.dialogs.BolusProgressDialog
import app.aaps.ui.dialogs.CalibrationDialog
import app.aaps.ui.dialogs.CarbsDialog
import app.aaps.ui.dialogs.CareDialog
import app.aaps.ui.dialogs.ExtendedBolusDialog
import app.aaps.ui.dialogs.FillDialog
import app.aaps.ui.dialogs.InsulinDialog
import app.aaps.ui.dialogs.LoopDialog
import app.aaps.ui.dialogs.ProfileSwitchDialog
import app.aaps.ui.dialogs.SiteRotationDialog
import app.aaps.ui.dialogs.TempBasalDialog
import app.aaps.ui.dialogs.TempTargetDialog
import app.aaps.ui.dialogs.TreatmentDialog
import app.aaps.ui.dialogs.WizardDialog
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.services.AlarmSoundServiceHelper
import app.aaps.ui.widget.Widget
import dagger.Lazy
import dagger.Reusable
import javax.inject.Inject

@Suppress("DEPRECATION")
@Reusable
class UiInteractionImpl @Inject constructor(
    private val context: Context,
    rxBus: RxBus,
    private val alarmSoundServiceHelper: AlarmSoundServiceHelper,
    private val protectionCheck: Lazy<ProtectionCheck>,
    preferences: Preferences
) : UiInteraction {

    private val alertDialogs: AlertDialogs = AlertDialogs(preferences, rxBus)

    override val mainActivity: Class<*> = MainActivity::class.java
    override val tddStatsActivity: Class<*> = TDDStatsActivity::class.java
    override val historyBrowseActivity: Class<*> = HistoryBrowseActivity::class.java
    override val errorHelperActivity: Class<*> = ErrorActivity::class.java
    override val bolusProgressHelperActivity: Class<*> = BolusProgressHelperActivity::class.java
    override val singleFragmentActivity: Class<*> = SingleFragmentActivity::class.java
    override val preferencesActivity: Class<*> = PreferencesActivity::class.java
    override val myPreferenceFragment: Class<*> = MyPreferenceFragment::class.java
    override val quickWizardListActivity: Class<*> = QuickWizardListActivity::class.java

    override val unitsEntries = arrayOf<CharSequence>("mg/dL", "mmol/L")
    override val unitsValues = arrayOf<CharSequence>("mg/dl", "mmol")

    override fun runAlarm(status: String, title: String, @RawRes soundId: Int) {
        val i = Intent(context, errorHelperActivity)
        i.putExtra(AlarmSoundService.SOUND_ID, soundId)
        i.putExtra(AlarmSoundService.STATUS, status)
        i.putExtra(AlarmSoundService.TITLE, title)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    override fun updateWidget(context: Context, from: String) {
        Widget.updateWidget(context, from)
    }

    override fun runWizardDialog(fragmentManager: FragmentManager, carbs: Int?, name: String?) {
        WizardDialog().also { dialog ->
            dialog.arguments = Bundle().also { bundle ->
                carbs?.let { bundle.putDouble("carbs_input", carbs.toDouble()) }
                name?.let { bundle.putString("notes_input", " $name - ${carbs}g") }
            }
        }.show(fragmentManager, "Food Item")

    }

    override fun runLoopDialog(fragmentManager: FragmentManager, showOkCancel: Int) {
        LoopDialog()
            .also { it.arguments = Bundle().also { bundle -> bundle.putInt("showOkCancel", showOkCancel) } }
            .show(fragmentManager, "LoopDialog")
    }

    override fun runProfileSwitchDialog(fragmentManager: FragmentManager, profileName: String?, iCfg: ICfg?) {
        ProfileSwitchDialog()
            .also {
                it.arguments = Bundle().also { bundle ->
                    bundle.putString("profileName", profileName)
                    iCfg?.let { bundle.putString("iCfg", it.toJson().toString()) }
                }
            }
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
        FillDialog(fragmentManager)
            .show(fragmentManager, "FillDialog")
    }

    override fun runSiteRotationDialog(fragmentManager: FragmentManager) {
        SiteRotationDialog()
            .show(fragmentManager, "SiteRotationDialog")
    }

    override fun runProfileViewerActivity(context: Context, time: Long, mode: UiInteraction.Mode, customProfile: String?, customProfileName: String?, customProfile2: String?) {
        val intent = Intent(context, ProfileViewerActivity::class.java).apply {
            putExtra("time", time)
            putExtra("mode", mode.ordinal)
            putExtra("customProfile", customProfile)
            putExtra("customProfileName", customProfileName)
            putExtra("customProfile2", customProfile2)
        }
        context.startActivity(intent)
    }

    override fun runCareDialog(fragmentManager: FragmentManager, options: UiInteraction.EventType, @StringRes event: Int) {
        CareDialog(fragmentManager)
            .also {
                it.arguments = Bundle().also { bundle ->
                    bundle.putInt("event", event)
                    bundle.putInt("options", options.ordinal)
                }
            }
            .show(fragmentManager, "CareDialog")
    }

    override fun runPreferencesForPlugin(activity: FragmentActivity, pluginSimpleName: String?) {
        pluginSimpleName ?: return
        protectionCheck.get().queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, {
            activity.startActivity(
                Intent(activity, PreferencesActivity::class.java)
                    .setAction("info.nightscout.androidaps.MainActivity")
                    .putExtra(UiInteraction.PLUGIN_NAME, pluginSimpleName)
            )
        })
    }

    override fun runBolusProgressDialog(fragmentManager: FragmentManager) {
        // Activity may be destroyed before Dialog pop up so try/catch
        try {
            BolusProgressDialog().show(fragmentManager, "BolusProgress")
        } catch (_: Exception) {
            // do nothing
        }
    }

    override fun startAlarm(@RawRes sound: Int, reason: String) {
        alarmSoundServiceHelper.startAlarm(sound, reason)
    }

    override fun stopAlarm(reason: String) {
        alarmSoundServiceHelper.stopAlarm(reason)
    }

    override fun showOkDialog(context: Context, title: String, message: String, onFinish: (() -> Unit)?) {
        alertDialogs.showOkDialog(context, title, message, onFinish)
    }

    override fun showOkDialog(context: Context, title: Int, message: Int, onFinish: (() -> Unit)?) {
        alertDialogs.showOkDialog(context, title, message, onFinish)
    }

    override fun showOkCancelDialog(context: Context, title: Int, message: Int, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, ok, cancel, icon)
    }

    override fun showOkCancelDialog(context: Context, title: String, message: String, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, ok, cancel, icon)
    }

    override fun showOkCancelDialog(context: Context, title: String, message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, secondMessage, ok, cancel, icon)
    }

    override fun showYesNoCancel(context: Context, title: Int, message: Int, yes: (() -> Unit)?, no: (() -> Unit)?) {
        alertDialogs.showYesNoCancel(context, title, message, yes, no)
    }

    override fun showYesNoCancel(context: Context, title: String, message: String, yes: (() -> Unit)?, no: (() -> Unit)?) {
        alertDialogs.showYesNoCancel(context, title, message, yes, no)
    }

    override fun showError(context: Context, title: String, message: String, positiveButton: Int?, ok: (() -> Unit)?, cancel: (() -> Unit)?) {
        alertDialogs.showError(context, title, message, positiveButton, ok, cancel)
    }
}