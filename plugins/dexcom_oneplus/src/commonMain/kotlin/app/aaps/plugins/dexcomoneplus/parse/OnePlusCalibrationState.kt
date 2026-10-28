package app.aaps.plugins.dexcomoneplus.parse

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.warmup.OnePlusWarmupClock
import kotlin.time.Clock

/**
 * Calibration / sensor state byte from EGV packets.
 *
 * Provenance: xDrip `CalibrationState` at pin `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
enum class OnePlusCalibrationState(val value: Int) {
    Unknown(0x00),
    Stopped(0x01),
    WarmingUp(0x02),
    ExcessNoise(0x03),
    NeedsFirstCalibration(0x04),
    NeedsSecondCalibration(0x05),
    Ok(0x06),
    NeedsCalibration(0x07),
    SensorFailed(0x0b),
    Ended(0x0f),
    SensorExpired(0x18),
    SensorStarted(0xC1),
    SensorStopped(0xC2),
    CalibrationSent(0xC3),
    Other(-1),
    ;

    fun usableGlucose(): Boolean = this == Ok || this == NeedsCalibration

    fun warmingUp(): Boolean = this == WarmingUp

    fun failed(): Boolean = this == SensorFailed || this == Ended || this == SensorExpired || this == SensorStopped

    companion object {
        fun parse(raw: Int): OnePlusCalibrationState {
            val v = raw and 0xff
            return entries.firstOrNull { it.value == v } ?: Other
        }
    }
}

object OnePlusCalibrationMapper {

    /**
     * Maps calibration byte + optional session age (seconds from EGV2) into [OnePlusWarmupState].
     * When warming up without exact remaining, uses [OnePlusWarmupClock] 30 min fallback from [nowMs].
     */
    fun toWarmupState(
        state: OnePlusCalibrationState,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
        sessionAgeSeconds: Int? = null,
    ): OnePlusWarmupState {
        return when {
            state.warmingUp() || state == OnePlusCalibrationState.SensorStarted -> {
                if (sessionAgeSeconds != null && sessionAgeSeconds in 0 until OnePlusWarmupClock.DEFAULT_WARMUP_MS.toInt() / 1000) {
                    val remaining = (OnePlusWarmupClock.DEFAULT_WARMUP_MS - sessionAgeSeconds * 1000L).coerceAtLeast(0L)
                    OnePlusWarmupState(
                        phase = OnePlusWarmupState.Phase.WARMING,
                        remainingMs = remaining,
                        endsAtEpochMs = nowMs + remaining,
                        message = state.name,
                    )
                } else {
                    OnePlusWarmupClock.warmingFromStart(startEpochMs = nowMs, nowMs = nowMs, message = state.name)
                }
            }
            state.usableGlucose() || state == OnePlusCalibrationState.Ok ->
                OnePlusWarmupState(phase = OnePlusWarmupState.Phase.READY, remainingMs = 0L, message = state.name)
            state.failed() ->
                OnePlusWarmupState(phase = OnePlusWarmupState.Phase.FAILED, message = state.name)
            else ->
                OnePlusWarmupState(phase = OnePlusWarmupState.Phase.WARMING, message = state.name)
        }
    }
}
