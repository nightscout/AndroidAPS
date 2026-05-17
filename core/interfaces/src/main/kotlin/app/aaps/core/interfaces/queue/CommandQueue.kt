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
    fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?): Boolean
    fun cancelAllBoluses(id: Long?)
    fun stopPump(callback: Callback?)
    fun startPump(callback: Callback?)
    fun setTBROverNotification(callback: Callback?, enable: Boolean)
    fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean
    fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean
    fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?): Boolean
    fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false, callback: Callback?): Boolean
    fun cancelExtended(callback: Callback?): Boolean
    fun readStatus(reason: String, callback: Callback?): Boolean
    fun statusInQueue(): Boolean
    fun loadHistory(type: Byte, callback: Callback?): Boolean
    fun setUserOptions(callback: Callback?): Boolean
    fun loadTDDs(callback: Callback?): Boolean
    fun loadEvents(callback: Callback?): Boolean
    fun clearAlarms(callback: Callback?): Boolean
    fun deactivate(callback: Callback?): Boolean
    fun updateTime(callback: Callback?): Boolean
    fun customCommand(customCommand: CustomCommand, callback: Callback?): Boolean
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
     * **Non-enqueue paths**: when the callback version returns `false` (command not enqueued),
     * the continuation is cancelled (`CancellationException` at the call site). However, some
     * rejection paths invoke the callback *before* returning `false` (e.g. running-mode gate);
     * in those cases the caller receives a `PumpEnactResult` with `success = false` instead of
     * a `CancellationException`. Callers should handle both outcomes.
     *
     * These overloads will be removed once the callback versions are deleted (future step).
     */

    suspend fun bolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!bolus(detailedBolusInfo, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!tempBasalAbsolute(absoluteRate, durationInMinutes, enforceNew, profile, tbrType, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!tempBasalPercent(percent, durationInMinutes, enforceNew, profile, tbrType, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun extendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!extendedBolus(insulin, durationInMinutes, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean = false): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!cancelTempBasal(enforceNew, autoForced, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun cancelExtended(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!cancelExtended(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun readStatus(reason: String): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!readStatus(reason, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
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
            if (!loadHistory(type, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun setUserOptions(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!setUserOptions(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun loadTDDs(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!loadTDDs(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun loadEvents(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!loadEvents(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun clearAlarms(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!clearAlarms(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun deactivate(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!deactivate(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun updateTime(): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!updateTime(object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }

    suspend fun customCommand(customCommand: CustomCommand): PumpEnactResult =
        suspendCancellableCoroutine { cont ->
            if (!customCommand(customCommand, object : Callback() {
                    override fun run() {
                        cont.resume(result)
                    }
                })) cont.cancel()
        }
}