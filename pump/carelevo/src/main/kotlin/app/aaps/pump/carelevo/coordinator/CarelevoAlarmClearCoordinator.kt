package app.aaps.pump.carelevo.coordinator

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.command.CmdAlarmClear
import app.aaps.pump.carelevo.command.CmdAlarmClearPatchDiscard
import app.aaps.pump.carelevo.command.CmdPumpResume
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * The alarm-clear BLE operations, shared by both clear-alarm surfaces (the Android notification action in
 * [app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler] and the in-app alarm screen in
 * [app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel]). The patch-facing ops go through
 * the AAPS CommandQueue so a resting (idle-disconnected) pump is reconnected before the op runs — a direct
 * write would silently no-op, and the callers' old `isPatchConnected()` gate would otherwise fall to the
 * destructive force-quit path. Each caller keeps its own alarm-cause branching, alarm-queue state and UI.
 */
@Singleton
class CarelevoAlarmClearCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val carelevoPatch: CarelevoPatch,
    private val transport: CarelevoBleTransport,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil
) {

    // Serialize the queue ops across BOTH surfaces (this is a @Singleton): CommandQueue.customCommand dedups
    // by command CLASS and returns success=false if one of the same class is already in-flight, so two
    // concurrent clears (notification + in-app, or two different alarms) would make the second misread as a
    // clear-failure and locally dismiss its alarm without ever clearing it on the patch. The mutex is held
    // for the whole command round-trip, so the second op waits and then runs its own clear.
    private val opMutex = Mutex()

    /** Acknowledge + clear the alarm ON the patch. Returns true when the patch confirmed the clear. */
    suspend fun clearAlarmOnPatch(info: CarelevoAlarmInfo): Boolean = opMutex.withLock {
        commandQueue.customCommand(CmdAlarmClear(info.alarmId, info.alarmType, info.cause)).success
    }

    /** Tell the patch to discard itself in response to a warning alarm. Returns true on confirmation. */
    suspend fun discardOnAlarm(info: CarelevoAlarmInfo): Boolean = opMutex.withLock {
        commandQueue.customCommand(CmdAlarmClearPatchDiscard(info.alarmId, info.alarmType, info.cause)).success
    }

    /**
     * Can a discard op plausibly reach the patch? Used by the patch-ABANDON paths (serious warning
     * discard) to decide between a graceful queue discard and an immediate local force-quit. With
     * per-op sessions there is no resting link to inspect — "reachable" means a session can be
     * attempted at all (patch paired + Bluetooth on); a genuinely dead patch then fails the bounded
     * session connect (~20 s) and falls to force-quit.
     */
    fun isPatchReachable(): Boolean =
        carelevoPatch.getPatchInfoAddress() != null && carelevoPatch.isBluetoothEnabled()

    /** Resume infusion after an auto-suspend alarm; syncs the TBR-cancel to NS on success. */
    suspend fun resumeInfusion(): Boolean = opMutex.withLock {
        val success = commandQueue.customCommand(CmdPumpResume()).success
        if (success) {
            // Only record a TBR stop if AAPS actually believes one is running — an unguarded stop
            // would write a spurious end-marker into treatment history on a plain resume.
            if (pumpSync.expectedPumpState().temporaryBasal != null) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = dateUtil.now(),
                    endPumpId = dateUtil.now(),
                    pumpType = PumpType.CAREMEDI_CARELEVO,
                    pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                )
            }
        }
        success
    }

    /**
     * Last-resort LOCAL teardown when the patch is being abandoned (patch-discard alarm, or the patch is
     * genuinely unreachable): clear the bond + flush local patch state. Deliberately NOT queued — it is a
     * one-way abandon (no resting link exists to drop). [onComplete] (the caller's alarm-queue clear)
     * runs AFTER unbond+flush — and runs even if the unbond errors — so the local alarm queue is only
     * cleared once the patch state has actually been flushed.
     */
    fun forceQuitTeardown(onComplete: () -> Unit) {
        try {
            carelevoPatch.getPatchInfoAddress()?.let { transport.adapter.removeBond(it.uppercase()) }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "forceQuitTeardown.unbondError", e)
        }
        carelevoPatch.flushPatchInformation()
        onComplete()
    }
}
