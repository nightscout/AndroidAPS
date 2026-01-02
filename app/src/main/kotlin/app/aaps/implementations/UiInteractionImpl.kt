package app.aaps.implementations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import app.aaps.MainActivity
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.MyPreferenceFragment
import app.aaps.activities.PreferencesActivity
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.main.general.overview.notifications.NotificationWithAction
import app.aaps.ui.activities.BolusProgressHelperActivity
import app.aaps.ui.activities.ErrorHelperActivity
import app.aaps.ui.activities.QuickWizardListActivity
import app.aaps.ui.activities.TDDStatsActivity
import app.aaps.ui.dialogs.BolusProgressDialog
import app.aaps.ui.dialogs.CalibrationDialog
import app.aaps.ui.dialogs.CarbsDialog
import app.aaps.ui.dialogs.CareDialog
import app.aaps.ui.dialogs.ExtendedBolusDialog
import app.aaps.ui.dialogs.FillDialog
import app.aaps.ui.dialogs.InsulinDialog
import app.aaps.ui.dialogs.LoopDialog
import app.aaps.ui.dialogs.ProfileSwitchDialog
import app.aaps.ui.dialogs.ProfileViewerDialog
import app.aaps.ui.dialogs.SiteRotationDialog
import app.aaps.ui.dialogs.TempBasalDialog
import app.aaps.ui.dialogs.TempTargetDialog
import app.aaps.ui.dialogs.TreatmentDialog
import app.aaps.ui.dialogs.WizardDialog
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.services.AlarmSoundServiceHelper
import app.aaps.ui.widget.Widget
import dagger.Reusable
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class UiInteractionImpl @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    private val alarmSoundServiceHelper: AlarmSoundServiceHelper,
    private val notificationWithActionProvider: Provider<NotificationWithAction>
) : UiInteraction {

    override val mainActivity: Class<*> = MainActivity::class.java
    override val tddStatsActivity: Class<*> = TDDStatsActivity::class.java
    override val historyBrowseActivity: Class<*> = HistoryBrowseActivity::class.java
    override val errorHelperActivity: Class<*> = ErrorHelperActivity::class.java
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
        FillDialog(fragmentManager)
            .show(fragmentManager, "FillDialog")
    }

    override fun runSiteRotationDialog(fragmentManager: FragmentManager) {
        SiteRotationDialog()
            .show(fragmentManager, "SiteRotationDialog")
    }

    override fun runProfileViewerDialog(fragmentManager: FragmentManager, time: Long, mode: UiInteraction.Mode, customProfile: String?, customProfileName: String?, customProfile2: String?) {
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

    override fun runBolusProgressDialog(fragmentManager: FragmentManager) {
        // Activity may be destroyed before Dialog pop up so try/catch
        try {
            BolusProgressDialog().show(fragmentManager, "BolusProgress")
        } catch (_: Exception) {
            // do nothing
        }
    }

    override fun dismissNotification(id: Int) {
        rxBus.send(EventDismissNotification(id))
    }

    override fun addNotification(id: Int, text: String, level: Int) {
        rxBus.send(EventNewNotification(Notification(id, text, level)))
    }

    override fun addNotificationValidFor(id: Int, text: String, level: Int, validMinutes: Int) {
        rxBus.send(EventNewNotification(Notification(id, text, level, validMinutes)))
    }

    override fun addNotificationWithSound(id: Int, text: String, level: Int, soundId: Int?) {
        rxBus.send(EventNewNotification(Notification(id, text, level).also { it.soundId = soundId }))
    }

    override fun addNotificationValidTo(id: Int, date: Long, text: String, level: Int, validTo: Long) {
        rxBus.send(EventNewNotification(Notification(id, System.currentTimeMillis(), text, level, validTo)))
    }

    override fun addNotificationWithAction(nsAlarm: NSAlarm) {
        rxBus.send(EventNewNotification(notificationWithActionProvider.get().with(nsAlarm)))
    }

    override fun addNotificationWithAction(id: Int, text: String, level: Int, buttonText: Int, action: Runnable, validityCheck: (() -> Boolean)?, @RawRes soundId: Int?, date: Long, validTo: Long) {
        rxBus.send(
            EventNewNotification(
                notificationWithActionProvider.get().with(id = id, text = text, level = level, validityCheck = validityCheck)
                    .action(buttonText, action)
                    .also {
                        it.date = date
                        it.soundId = soundId
                    }
            )
        )
    }

    override fun addNotificationWithDialogResponse(id: Int, text: String, level: Int, @StringRes buttonText: Int, title: String, message: String, validityCheck: (() -> Boolean)?) {
        rxBus.send(
            EventNewNotification(
                notificationWithActionProvider.get().with(id, text, level, validityCheck)
                    .also { n ->
                        n.action(buttonText) {
                            n.contextForAction?.let { OKDialog.show(it, title, message) }
                        }
                    })
        )
    }

    override fun addNotification(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, action: Runnable, validityCheck: (() -> Boolean)?) {
        rxBus.send(
            EventNewNotification(
                notificationWithActionProvider.get().with(id, text, level, validityCheck).apply {
                    action(actionButtonId, action)
                })
        )
    }

    override fun showToastAndNotification(ctx: Context, string: String, soundID: Int) {
        ToastUtils.showToastInUiThread(ctx, string)
        ToastUtils.playSound(ctx, soundID)
        addNotification(Notification.TOAST_ALARM, string, Notification.URGENT)
    }

    override fun startAlarm(@RawRes sound: Int, reason: String) {
        alarmSoundServiceHelper.startAlarm(sound, reason)
    }

    override fun stopAlarm(reason: String) {
        alarmSoundServiceHelper.stopAlarm(reason)
    }
}