package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.compose.AaruyHistoryTypes
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The steps of a reservoir change that move the motor. The command queue runs them and holds the link while one runs,
 * so no bolus or temp basal can come in between. They used to be sent from the screen, on a link that stayed up.
 */
@SingleIn(AppScope::class)
@Inject
class AaruyReplaceSteps(
    private val aaruyBle: AaruyBLE,
    private val aaruyPump: AaruyPump,
    private val dateUtil: DateUtil,
    private val historyUnitInfoDao: HistoryUnitInfoDao
) {

    /**
     * Whether the pump log has a rewind and no insulin given since. So a change of reservoir is known after an app restart
     * too, with no setting that an import of old settings could bring back. Only while the pump is stopped does it mean the
     * change is still going on.
     *
     * The prime does not tell how far the change came. Seen on a real pump: its prime entry is written only when the pump
     * leaves the stop, so a stopped pump never shows it. After a restart the change goes on after the rewind, with the prime.
     */
    suspend fun rewoundWithoutDeliverySince(): Boolean = withContext(Dispatchers.IO) {
        // Newest first.
        val entries = historyUnitInfoDao.querySince(dateUtil.now() - LOG_WINDOW_MS)
        val sinceRewind = entries.takeWhile { it.type != AaruyHistoryTypes.REWIND.toInt() }
        sinceRewind.size < entries.size && sinceRewind.none { it.type in DELIVERY_TYPES }
    }

    /** @return null for a command that is no step of a reservoir change */
    suspend fun execute(command: CustomCommand, result: PumpEnactResult): PumpEnactResult? = when (command) {
        is AaruyRewindCommand      -> result.withMotorResult(rewind())
        is AaruyPrimeCommand       -> result.apply { success = prime() }
        is AaruyFillCannulaCommand -> result.withMotorResult(fillCannula(command.needleType))
        is AaruyStartBasalCommand  -> result.apply { success = startBasal() }
        else                       -> null
    }

    /** A second rewind does no harm, so only a seen one counts. */
    suspend fun rewind(): AaruyMotorResult {
        // The queue lets a bolus command end before this. A bolus the pump runs with no command, as after an app restart, would be ended.
        if (aaruyPump.pumpState == AaruyPumpState.LARGEDOSE_RUNNING || !aaruyPump.bolusDone) return AaruyMotorResult.NOT_RUN
        return runMotor(AaruyPumpState.PUSHBACK_RUNNING, AaruyPumpState.STOP_RUNNING) { aaruyBle.replaceDevice() }
    }

    /** A real pump says it is done long before the piston stops, so its answer ends the wait. @return whether it answered */
    suspend fun prime(): Boolean = coroutineScope {
        // Listening before the command goes out: the answer can come before a later listener starts.
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(PRIME_TIMEOUT_MS) { aaruyPump.errCode.first { isPrimeAnswer(it) } }
        }
        aaruyBle.pushForward()
        answer.await() != null
    }

    /** @param needleType 0 is a 6 mm cannula, 1 is 9 mm */
    suspend fun fillCannula(needleType: Int): AaruyMotorResult {
        aaruyBle.syncAndLoadBasicParam()
        return runMotor(AaruyPumpState.EXHAUSTING, AaruyPumpState.STOP_RUNNING) { aaruyBle.needlePushAir(needleType.toByte()) }
    }

    /** Any delivery counts: on a second try the loop can have set a temp basal already, and the client then sends nothing. */
    suspend fun startBasal(): Boolean {
        aaruyPump.clearAlarmState()
        // The client of this app run may not know of the rewind, as after a restart. The log does.
        if (aaruyPump.pumpState == AaruyPumpState.STOP_RUNNING && rewoundWithoutDeliverySince()) aaruyBle.allowRestoreAfterRewind()
        aaruyBle.startBaseInject()
        val start = dateUtil.now()
        while (aaruyPump.pumpState !in AaruyPumpState.BASALRATE_RUNNING..AaruyPumpState.LARGEDOSE_RUNNING) {
            if (dateUtil.now() - start > BASAL_TIMEOUT_MS) return false
            delay(POLL_MS)
        }
        return true
    }

    /**
     * Sends a motor command and waits for the pump to pass through [during] and end in [end]. Seen on a real pump: the
     * pump is in [end] already before a cannula fill, so [end] alone says nothing: a refused or lost command looked done.
     */
    private suspend fun runMotor(during: AaruyPumpState, end: AaruyPumpState, send: () -> Unit): AaruyMotorResult {
        aaruyPump.forgetSeenStates()
        send()
        val start = dateUtil.now()
        while (dateUtil.now() - start < MOTOR_TIMEOUT_MS) {
            if (aaruyPump.hasSeen(during) && aaruyPump.pumpState == end) return AaruyMotorResult.DONE
            // The command may have reached the pump. Nothing connects again while a command of the queue runs.
            if (aaruyPump.connectionState != ConnectionState.CONNECTED) return AaruyMotorResult.UNKNOWN
            delay(POLL_MS)
        }
        return if (aaruyPump.hasSeen(during)) AaruyMotorResult.UNKNOWN else AaruyMotorResult.NOT_RUN
    }

    /** The pump sends one of these when the piston found the reservoir, also when it saw insulin come out. */
    private fun isPrimeAnswer(code: Int): Boolean =
        code shr 8 == 0 && (code and 0xFF).toByte() in listOf(
            AaruyConst.errCodeType.FLAG_LOCATE_POSITION,
            AaruyConst.errCodeType.FLAG_LOCATE_POS_LIQUID_DISCHARGE,
            AaruyConst.errCodeType.DISCHARGE_LIQUID
        )

    private companion object {

        const val POLL_MS = 50L

        // A real pump with a full reservoir rewound in 8 s.
        const val MOTOR_TIMEOUT_MS = 120_000L
        const val PRIME_TIMEOUT_MS = 30_000L
        const val BASAL_TIMEOUT_MS = 5_000L

        /** A pump stopped longer than this after a rewind is changed again from the start. */
        const val LOG_WINDOW_MS = 3 * 24 * 60 * 60_000L

        val DELIVERY_TYPES = setOf(AaruyHistoryTypes.BASAL.toInt(), AaruyHistoryTypes.TEMP_BASAL.toInt(), AaruyHistoryTypes.BOLUS.toInt())
    }
}
