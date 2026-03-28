package app.aaps.core.interfaces.queue

import android.text.Spanned
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync

/**
 * Serialized command queue for pump communication.
 *
 * All pump commands (bolus, temp basal, status reads, etc.) are submitted to this queue
 * and executed sequentially on a background thread. This ensures thread-safe pump communication
 * and prevents conflicting commands from being sent simultaneously.
 *
 * ## Usage Pattern
 * 1. Caller submits a command (e.g., [bolus], [tempBasalAbsolute]).
 * 2. Queue connects to the pump if not already connected.
 * 3. Command executes via the active [Pump] driver.
 * 4. Result is reported via [Callback].
 * 5. When queue is empty, pump is disconnected after [Pump.waitForDisconnectionInSeconds].
 *
 * ## Thread Safety
 * All methods are safe to call from any thread. Commands are enqueued and executed
 * on a dedicated worker thread managed by the implementation.
 *
 * @see Command
 * @see Callback
 * @see app.aaps.core.interfaces.pump.Pump
 */
interface CommandQueue {

    /** True if the queue is waiting for the pump to disconnect before processing next command. */
    var waitingForDisconnect: Boolean

    /** @return true if a command of the specified [type] is currently executing. */
    fun isRunning(type: Command.CommandType): Boolean

    /** Pick up the next queued command for execution. Called by the queue worker thread. */
    fun pickup()

    /** Remove all pending commands from the queue. Does not cancel the currently executing command. */
    fun clear()

    /** @return number of commands waiting in the queue (not including the currently executing one). */
    fun size(): Int

    /** @return the command currently being executed, or null if idle. */
    fun performing(): Command?

    /** Reset the currently performing command reference. Called after command completion. */
    fun resetPerforming()

    /** @return true if any bolus command (regular or SMB) is waiting in the queue. */
    fun bolusInQueue(): Boolean

    /**
     * Enqueue a bolus delivery command.
     *
     * @param detailedBolusInfo Bolus details including amount, type, and optional carbs.
     * @param callback Optional callback invoked when command completes.
     * @return true if command was successfully enqueued.
     */
    fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?): Boolean

    /**
     * Cancel all pending and currently executing bolus commands.
     * @param id Optional bolus ID to cancel; null cancels all.
     */
    fun cancelAllBoluses(id: Long?)

    /** Enqueue a stop-pump command (suspend insulin delivery). */
    fun stopPump(callback: Callback?)

    /** Enqueue a start-pump command (resume insulin delivery). */
    fun startPump(callback: Callback?)

    /** Enable or disable TBR-over notification on the pump. */
    fun setTBROverNotification(callback: Callback?, enable: Boolean)

    /**
     * Enqueue a temporary basal rate command in absolute units [U/h].
     *
     * @param absoluteRate Desired rate in U/h.
     * @param durationInMinutes Duration of the TBR.
     * @param enforceNew If true, force a new TBR even if the same rate is already running.
     * @param profile Current profile (used for U/h <-> % conversion if needed).
     * @param tbrType TBR type for database storage.
     * @param callback Optional completion callback.
     * @return true if command was enqueued.
     */
    fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean

    /**
     * Enqueue a temporary basal rate command in percent.
     *
     * @param percent Desired rate in % (100% = normal basal, 0% = zero temp).
     * @param durationInMinutes Duration of the TBR.
     * @param enforceNew If true, force a new TBR even if the same rate is already running.
     * @param profile Current profile (used for conversion if needed).
     * @param tbrType TBR type for database storage.
     * @param callback Optional completion callback.
     * @return true if command was enqueued.
     */
    fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean

    /**
     * Enqueue an extended bolus command.
     *
     * @param insulin Total insulin to deliver over the duration [U].
     * @param durationInMinutes Duration of the extended bolus.
     * @param callback Optional completion callback.
     * @return true if command was enqueued.
     */
    fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?): Boolean

    /**
     * Cancel the currently running temporary basal rate.
     *
     * @param enforceNew If true, always send a real cancel command (no workarounds).
     * @param autoForced If true, this cancellation was triggered automatically by the system.
     * @param callback Optional completion callback.
     * @return true if command was enqueued.
     */
    fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false, callback: Callback?): Boolean

    /** Cancel the currently running extended bolus. */
    fun cancelExtended(callback: Callback?): Boolean

    /**
     * Enqueue a pump status read command.
     *
     * @param reason Human-readable reason for the status read (for logging).
     * @param callback Optional completion callback.
     * @return true if command was enqueued.
     */
    fun readStatus(reason: String, callback: Callback?): Boolean

    /** @return true if a read-status command is already queued. */
    fun statusInQueue(): Boolean

    /** Load pump history of the specified [type]. */
    fun loadHistory(type: Byte, callback: Callback?): Boolean

    /** Upload user settings to the pump. */
    fun setUserOptions(callback: Callback?): Boolean

    /** Load Total Daily Doses from the pump. */
    fun loadTDDs(callback: Callback?): Boolean

    /** Load pump events/history records. */
    fun loadEvents(callback: Callback?): Boolean

    /** Clear alarms on the pump. */
    fun clearAlarms(callback: Callback?): Boolean

    /** Deactivate/unpair the pump (e.g., for pod-based pumps). */
    fun deactivate(callback: Callback?): Boolean

    /** Synchronize the pump's internal clock. */
    fun updateTime(callback: Callback?): Boolean

    /**
     * Enqueue a custom command specific to the pump driver.
     *
     * @param customCommand The custom command to execute.
     * @param callback Optional completion callback.
     * @return true if command was enqueued.
     * @see CustomCommand
     */
    fun customCommand(customCommand: CustomCommand, callback: Callback?): Boolean

    /** @return true if a custom command of the specified type is currently executing. */
    fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean

    /** @return true if a custom command of the specified type is waiting in the queue. */
    fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean

    /** @return HTML-formatted string showing current queue status for UI display. */
    fun spannedStatus(): Spanned

    /** @return true if the pump is currently running the [requestedProfile]. */
    fun isThisProfileSet(requestedProfile: Profile): Boolean
}