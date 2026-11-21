package app.aaps.implementation.maintenance

import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.time.Duration

/**
 * The checks the app makes every 5 minutes, on every platform:
 * - the pump status is read when it is old, and the profile is set again when the pump runs another one,
 * - the pump unreachable alarm,
 * - a missing zero temp basal is set again while the loop is paused,
 * - the status is uploaded to Nightscout when the loop does not do it,
 * - the clock change check and the [PeriodicMaintenance].
 *
 * Only what starts a pass is different per platform. Android runs it from `KeepAliveWorker` every 5 minutes. iOS has
 * no timer that runs while the app sleeps, so it runs a pass when the app is woken, for example by a Bluetooth event,
 * and gives [runOnce] a `minInterval` so that a pass that comes too soon is skipped.
 */
@SingleIn(AppScope::class)
@Inject
class KeepAliveRunner(
    private val aapsLogger: AAPSLogger,
    private val fabricPrivacy: FabricPrivacy,
    private val localAlertUtils: LocalAlertUtils,
    private val config: Config,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val periodicMaintenance: PeriodicMaintenance,
    private val rh: TextResolver,
    private val dstHelper: DstHelper,
    private val ch: ConcentrationHelper
) {

    /** One pass at a time, also when two wake-ups come together. */
    private val passLock = Mutex()

    /** When the last pass started, 0 before the first. */
    @Volatile
    var lastRun: Long = 0
        internal set

    /** When a pass last asked the pump for its status, 0 before the first time. */
    internal var lastReadStatus: Long = 0

    /** When a pass last uploaded the status to Nightscout, 0 before the first time. */
    internal var lastIobUpload: Long = 0

    /**
     * One pass.
     *
     * @param minInterval when set, the pass is skipped if the last one started less than this long ago.
     * @return [WorkOutcome.Retry] when the app has not finished starting, [WorkOutcome.Skipped] when the pass came too
     * soon or another pass is running, [WorkOutcome.Success] when the pass ran.
     */
    suspend fun runOnce(minInterval: Duration? = null): WorkOutcome {
        if (minInterval != null && lastRun + minInterval.inWholeMilliseconds > dateUtil.now())
            return WorkOutcome.Skipped("Schedule broken. Ignoring")

        // Gate the plugin-touching work behind app init: a pass that comes after a reboot, before the app start has
        // filled the plugin store, would crash.
        if (!config.awaitInitialized()) {
            aapsLogger.debug(LTag.CORE, "KeepAlive: app not yet initialized, retrying")
            return WorkOutcome.Retry("app not yet initialized")
        }

        if (!passLock.tryLock()) return WorkOutcome.Skipped("Another pass is running")
        try {
            if (lastRun != 0L && dateUtil.now() - lastRun > T.mins(10).msecs()) {
                aapsLogger.error(LTag.CORE, "KeepAlive fail")
                fabricPrivacy.logCustom("KeepAliveFail")
            }
            lastRun = dateUtil.now()

            dstHelper.dstCheck()
            periodicMaintenance.runOnce()
            checkPump()
            // Retries a missing zero TBR while the pump is disconnected - nothing else does, as the loop is paused
            loop.verifyZeroDelivery()
            checkAPS()
            return WorkOutcome.Success
        } finally {
            passLock.unlock()
        }
    }

    // Usually deviceStatus is uploaded through LoopPlugin after every loop cycle.
    // if there is no BG available, we have to upload anyway to have correct
    // IOB displayed in NS
    internal suspend fun checkAPS() {
        var shouldUploadStatus = false
        if (config.AAPSCLIENT) return
        if (config.PUMPCONTROL) shouldUploadStatus = true
        else if (!loop.runningMode().isLoopRunning() || iobCobCalculator.ads.actualBg() == null) shouldUploadStatus = true
        else if (activePlugin.activeAPS?.let { dateUtil.isOlderThan(it.lastAPSRun, 5) } == true) shouldUploadStatus = true
        if (dateUtil.isOlderThan(lastIobUpload, IOB_UPDATE_FREQUENCY_IN_MINUTES) && shouldUploadStatus) {
            lastIobUpload = dateUtil.now()
            loop.scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
        }
    }

    internal suspend fun checkPump() {
        val pump = activePlugin.activePump
        val ps = profileFunction.getRequestedProfile() ?: return
        val requestedProfile = ProfileSealed.PS(ps, activePlugin)
        val runningProfile = profileFunction.getProfile()
        val lastConnection = pump.lastDataTime.value
        val now = dateUtil.now()
        val isStatusOutdated = lastConnection + STATUS_UPDATE_FREQUENCY < now
        // Against the running profile: it holds the rate the pump really gives, which can be rounded to its step
        // (#2454). The requested one differed by up to a step, and a pump that rounds was read every 5 minutes.
        // A requested profile that is not running yet is caught first, by the profile check below.
        val isBasalOutdated = abs((runningProfile ?: requestedProfile).getBasal() - ch.fromPump(pump.baseBasalRate)) > pump.pumpDescription.basalStep
        aapsLogger.debug(LTag.CORE, "Last connection: " + dateUtil.dateAndTimeString(lastConnection))
        // Sometimes it can happen that keepalive is not triggered every 5 minutes as it should.
        // In some cases, it may not even have been started at all.
        // If these cases aren't handled, false "pump unreachable" alarms can be produced.
        // Avoid this by checking that (a) readStatus was requested at least once (lastReadStatus
        // is != 0 in that case) and (b) the last read status request was not too long ago.
        //
        // Also, use 5:30 as the threshold for (b) above instead of 5 minutes sharp. The keepalive
        // checks come in 5 minute intervals, but due to temporal jitter, the interval between the
        // last read status attempt and the current time can be slightly over 5 minutes (for example,
        // 300041 milliseconds instead of exactly 300000). Add 30 extra seconds to allow for
        // plenty of tolerance.
        val runningMode = loop.runningMode()
        if (lastReadStatus != 0L && (now - lastReadStatus).coerceIn(minimumValue = 0, maximumValue = null) <= T.secs(5 * 60 + 30).msecs()) {
            localAlertUtils.checkPumpUnreachableAlarm(lastConnection, isStatusOutdated, runningMode == RM.Mode.DISCONNECTED_PUMP)
        }
        if (runningMode == RM.Mode.DISCONNECTED_PUMP) {
            // do nothing if pump is disconnected
        } else if (
            runningProfile == null ||
            (
                (!pump.isThisProfileSet(requestedProfile) ||
                    !requestedProfile.isAppliedAs(runningProfile) ||
                    (runningProfile is ProfileSealed.EPS && runningProfile.value.originalEnd < dateUtil.now() && runningProfile.value.originalDuration != 0L)
                    )
                    && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)
                )
        ) {
            rxBus.send(EventProfileChangeRequested())
        } else if (isStatusOutdated && !pump.isBusy()) {
            lastReadStatus = now
            commandQueue.readStatus(rh.gs(CoreUiStrings.keepalive_status_outdated))
        } else if (isBasalOutdated && !pump.isBusy()) {
            lastReadStatus = now
            commandQueue.readStatus(rh.gs(CoreUiStrings.keepalive_basal_outdated))
        }
    }

    private companion object {

        val STATUS_UPDATE_FREQUENCY = T.mins(15).msecs()
        const val IOB_UPDATE_FREQUENCY_IN_MINUTES = 5L
    }
}
