package app.aaps.core.interfaces.queue

import app.aaps.core.keys.interfaces.TextRef
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * **Deadlock warning** — the queue is processed by a single app-owned `CommandExecutor` loop; one
 * command at a time. Awaiting a suspend method on this interface from inside the body of another
 * queued command's `execute()` (directly, or transitively via `Pump.getPumpStatus()`,
 * `Pump.deliverTreatment()`, BLE message handlers running on the SerialIOThread, etc.) will
 * deadlock: the awaited command sits in the queue waiting for the executor, but the executor is
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

    /** True while a [withHold] block is running. The executor asks this before it picks up a command. */
    fun isHeld(): Boolean

    /**
     * Runs [block] with the queue held: nothing new is picked up until it returns.
     *
     * Waits up to [timeout] for the command already in flight to finish first, and returns false
     * without running [block] if it does not. Commands waiting in the queue are kept, not cancelled -
     * they run as soon as the hold ends.
     *
     * This exists for applying imported settings, which stops and starts pump drivers. Sampling
     * [size] and [performing] and then applying is not enough on its own: the loop or an automation
     * can enqueue between the check and the teardown, and that command would start against a driver
     * that is being pulled out from under it.
     *
     * The hold is always released, including when [block] throws. Do not call this from inside a
     * queued command's `execute()` - see the deadlock warning on this interface.
     */
    suspend fun withHold(reason: String, timeout: Duration, block: suspend () -> Unit): Boolean

    fun clear()
    // TextRef, not an @StringRes Int: this interface is commonMain, and a resource id here would pin
    // every implementation and every caller to Android. `comment` already has a TextRef overload.
    fun completeAllAsNoOp(comment: TextRef)
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
    fun isCustomCommandRunning(customCommandType: KClass<out CustomCommand>): Boolean
    fun isCustomCommandInQueue(customCommandType: KClass<out CustomCommand>): Boolean
    fun statusAsAnnotated(): AnnotatedString
    suspend fun isThisProfileSet(requestedProfile: EffectiveProfile): Boolean
}