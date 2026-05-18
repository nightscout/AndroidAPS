package app.aaps.core.interfaces.queue

import android.text.Spanned
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?)
    fun cancelAllBoluses(id: Long?)
    fun stopPump(callback: Callback?)
    fun startPump(callback: Callback?)
    fun setTBROverNotification(callback: Callback?, enable: Boolean)
    fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?)
    fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?)
    fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?)
    fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false, callback: Callback?)
    fun cancelExtended(callback: Callback?)
    fun readStatus(reason: String, callback: Callback?)
    fun statusInQueue(): Boolean
    fun loadHistory(type: Byte, callback: Callback?)
    fun setUserOptions(callback: Callback?)
    fun loadTDDs(callback: Callback?)
    fun loadEvents(callback: Callback?)
    fun clearAlarms(callback: Callback?)
    fun deactivate(callback: Callback?)
    suspend fun updateTime(): PumpEnactResult
    fun customCommand(customCommand: CustomCommand, callback: Callback?)
    fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean
    fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean
    fun spannedStatus(): Spanned
    suspend fun isThisProfileSet(requestedProfile: EffectiveProfile): Boolean

    /**
     * Suspend overloads for every callback-based command. Each bridges to the existing callback
     * version via [suspendCancellableCoroutine] so callers get linear, structured-concurrency-
     * friendly code without a callback object.
     *
     * **Cancellation**: cancelling the caller's scope does NOT abort the pump command already
     * executing — the queue has no per-command abort mechanism. The command will still run to
     * completion; only the caller stops waiting for the result.
     *
     * The callback-based methods always invoke the callback (either immediately on rejection or
     * when the pump command completes), so the continuation always resumes normally —
     * CancellationException is never thrown at these call sites.
     *
     * These overloads will be removed once the callback versions are deleted (future step).
     */

    suspend fun bolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            bolus(detailedBolusInfo, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            tempBasalAbsolute(absoluteRate, durationInMinutes, enforceNew, profile, tbrType, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            tempBasalPercent(percent, durationInMinutes, enforceNew, profile, tbrType, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun extendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            extendedBolus(insulin, durationInMinutes, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            cancelTempBasal(enforceNew, autoForced, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun cancelExtended(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            cancelExtended(object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun readStatus(reason: String): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            readStatus(reason, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun stopPump(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            stopPump(object : Callback() {
                override fun run() {
                    cont.resume(result)
                }
            })
        }

    suspend fun startPump(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            startPump(object : Callback() {
                override fun run() {
                    cont.resume(result)
                }
            })
        }

    suspend fun setTBROverNotification(enable: Boolean): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            setTBROverNotification(object : Callback() {
                override fun run() {
                    cont.resume(result)
                }
            }, enable)
        }

    suspend fun loadHistory(type: Byte): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            loadHistory(type, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun setUserOptions(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            setUserOptions(object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun loadTDDs(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            loadTDDs(object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun loadEvents(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            loadEvents(object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun clearAlarms(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            clearAlarms(object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun deactivate(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            deactivate(object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }

    suspend fun customCommand(customCommand: CustomCommand): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            customCommand(customCommand, object : Callback() {
                override fun run() { cont.resume(result) }
            })
        }
}