package app.aaps.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import app.aaps.pump.omnipod.dash.util.Constants
import app.aaps.pump.omnipod.dash.util.I8n
import app.aaps.pump.omnipod.dash.util.mapProfileToBasalProgram
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class DashInsertCannulaViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    private val profileFunction: ProfileFunction,
    private val pumpSync: PumpSync,
    private val podStateManager: OmnipodDashPodStateManager,
    private val rxBus: RxBus,
    private val sp: SP,
    private val rh: ResourceHelper,
    private val fabricPrivacy: FabricPrivacy,
    private val history: DashHistory,
    instantiator: Instantiator,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : InsertCannulaViewModel(instantiator, logger, aapsSchedulers) {

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
            val expirationReminderEnabled = sp.getBoolean(app.aaps.pump.omnipod.common.R.string.key_omnipod_common_expiration_reminder_enabled, true)
            val expirationReminderHours = sp.getInt(app.aaps.pump.omnipod.common.R.string.key_omnipod_common_expiration_reminder_hours_before_expiry, 9)

            val expirationReminderHoursBeforeShutdown = if (expirationReminderEnabled)
                expirationReminderHours.toLong()
            else
                null

            val expirationAlarmEnabled = sp.getBoolean(app.aaps.pump.omnipod.common.R.string.key_omnipod_common_expiration_alarm_enabled, true)
            val expirationAlarmHours = sp.getInt(app.aaps.pump.omnipod.common.R.string.key_omnipod_common_expiration_alarm_hours_before_shutdown, 8)

            val expirationAlarmHoursBeforeShutdown = if (expirationAlarmEnabled)
                expirationAlarmHours.toLong()
            else
                null

            super.disposable += omnipodManager.activatePodPart2(basalProgram, expirationReminderHoursBeforeShutdown, expirationAlarmHoursBeforeShutdown)
                .ignoreElements()
                .andThen(podStateManager.updateExpirationAlertSettings(expirationReminderEnabled, expirationReminderHours, expirationAlarmEnabled, expirationAlarmHours))
                .andThen(
                    history.createRecord(
                        OmnipodCommandType.INSERT_CANNULA,
                        basalProfileRecord = BasalValuesRecord(profile.getBasalValues().toList()),
                        initialResult = InitialResult.SENT,
                        resolveResult = ResolvedResult.SUCCESS,
                        resolvedAt = System.currentTimeMillis(),
                    ).ignoreElement()
                )
                .subscribeBy(
                    onError = { throwable ->
                        logger.error(LTag.PUMP, "Error in Pod activation part 2", throwable)
                        source.onSuccess(instantiator.providePumpEnactResult().success(false).comment(I8n.textFromException(throwable, rh)))
                    },
                    onComplete = {
                        logger.debug("Pod activation part 2 completed")
                        podStateManager.basalProgram = basalProgram

                        pumpSync.syncStopTemporaryBasalWithPumpId(
                            timestamp = System.currentTimeMillis(),
                            endPumpId = System.currentTimeMillis(),
                            pumpType = PumpType.OMNIPOD_DASH,
                            pumpSerial = Constants.PUMP_SERIAL_FOR_FAKE_TBR // cancel the fake TBR with the same pump
                            // serial that it was created with
                        )

                        pumpSync.connectNewPump()

                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = System.currentTimeMillis(),
                            type = TE.Type.CANNULA_CHANGE,
                            pumpType = PumpType.OMNIPOD_DASH,
                            pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                        )
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = System.currentTimeMillis(),
                            type = TE.Type.INSULIN_CHANGE,
                            pumpType = PumpType.OMNIPOD_DASH,
                            pumpSerial = podStateManager.uniqueId?.toString() ?: "n/a"
                        )
                        rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED))
                        fabricPrivacy.logCustom("OmnipodDashPodActivated")
                        source.onSuccess(instantiator.providePumpEnactResult().success(true))
                    }
                )
        }
    }

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_insert_cannula_title

    @StringRes
    override fun getTextId() = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_insert_cannula_text
}
