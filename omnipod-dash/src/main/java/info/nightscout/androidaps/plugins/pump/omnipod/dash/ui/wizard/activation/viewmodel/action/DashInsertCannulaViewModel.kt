package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.mapProfileToBasalProgram
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

class DashInsertCannulaViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    private val profileFunction: ProfileFunction,
    private val pumpSync: PumpSync,
    private val podStateManager: OmnipodDashPodStateManager,
    private val rxBus: RxBusWrapper,
    private val sp: SP,

    injector: HasAndroidInjector,
    logger: AAPSLogger
) : InsertCannulaViewModel(injector, logger) {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.create { source ->
        val profile = profileFunction.getProfile()
        if (profile == null) {
            source.onError(IllegalStateException("No profile set"))
        } else {
            val basalProgram = mapProfileToBasalProgram(profile)
            logger.debug(
                LTag.PUMPCOMM,
                "Mapped profile to basal program. profile={}, basalProgram={}",
                profile,
                basalProgram
            )
            val expirationReminderEnabled = sp.getBoolean(R.string.key_omnipod_common_expiration_reminder_enabled, true)
            val expirationHours = sp.getInt(R.string.key_omnipod_common_expiration_reminder_hours_before_shutdown, 9)

            val expirationHoursBeforeShutdown = if (expirationReminderEnabled)
                expirationHours.toLong()
            else
                null

            val disposable = omnipodManager.activatePodPart2(basalProgram, expirationHoursBeforeShutdown).subscribeBy(
                onNext = { podEvent ->
                    logger.debug(
                        LTag.PUMP,
                        "Received PodEvent in Pod activation part 2: $podEvent"
                    )
                },
                onError = { throwable ->
                    logger.error(LTag.PUMP, "Error in Pod activation part 2", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.toString()))
                },
                onComplete = {
                    logger.debug("Pod activation part 2 completed")
                    podStateManager.basalProgram = basalProgram
                    pumpSync.connectNewPump()
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = DetailedBolusInfo.EventType.CANNULA_CHANGE,
                        pumpType = PumpType.OMNIPOD_DASH,
                        pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                    )
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = DetailedBolusInfo.EventType.INSULIN_CHANGE,
                        pumpType = PumpType.OMNIPOD_DASH,
                        pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                    )
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        timestamp = System.currentTimeMillis(),
                        endPumpId = System.currentTimeMillis(),
                        pumpType = PumpType.OMNIPOD_DASH,
                        pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                    )
                    podStateManager.updateExpirationAlertSettings(expirationReminderEnabled, expirationHours)
                    rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED))
                    source.onSuccess(PumpEnactResult(injector).success(true))
                }
            )
        }
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_insert_cannula_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_activation_wizard_insert_cannula_text
}
