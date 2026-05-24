package app.aaps.core.interfaces.queue

import android.text.Spanned
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync

/**
 * **Deadlock warning** — the queue is processed by a single [QueueWorker]; one command at a
 * time. Awaiting a suspend method on this interface from inside the body of another queued
 * command's `execute()` (directly, or transitively via `Pump.getPumpStatus()`,
 * `Pump.deliverTreatment()`, BLE message handlers running on the SerialIOThread, etc.) will
 * deadlock: the awaited command sits in the queue waiting for the worker, but the worker is
 * busy executing the caller.
 *
 * If you need to enqueue another command from such a context, do not await — fire-and-forget
 * via a scope that outlives the current call:
 *
 * ```
 * pluginScope.launch { commandQueue.readStatus(reason) }   // pump plugin
 * appScope.launch { commandQueue.readStatus(reason) }      // BLE handlers, services
 * ```
 */
interface CommandQueue {

    var waitingForDisconnect: Boolean

    fun isRunning(type: Command.CommandType): Boolean
    fun pickup()
    fun clear()
    fun completeAllAsNoOp(commentResId: Int)
    fun size(): Int
    fun performing(): Command?
    fun resetPerforming()
    fun bolusInQueue(): Boolean
    suspend fun bolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult
    fun cancelAllBoluses(id: Long?)
    suspend fun stopPump(): PumpEnactResult
    suspend fun startPump(): PumpEnactResult
    suspend fun setTBROverNotification(enable: Boolean): PumpEnactResult
    suspend fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult
    suspend fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult
    suspend fun extendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult
    suspend fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false): PumpEnactResult
    suspend fun cancelExtended(): PumpEnactResult
    suspend fun readStatus(reason: String): PumpEnactResult
    fun statusInQueue(): Boolean
    suspend fun loadHistory(type: Byte): PumpEnactResult
    suspend fun setUserOptions(): PumpEnactResult
    suspend fun loadTDDs(): PumpEnactResult
    suspend fun loadEvents(): PumpEnactResult
    suspend fun clearAlarms(): PumpEnactResult
    suspend fun deactivate(): PumpEnactResult
    suspend fun updateTime(): PumpEnactResult
    suspend fun customCommand(customCommand: CustomCommand): PumpEnactResult
    fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean
    fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean
    fun spannedStatus(): Spanned
    suspend fun isThisProfileSet(requestedProfile: EffectiveProfile): Boolean
}