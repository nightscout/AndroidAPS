package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.onePlusSleep
import app.aaps.plugins.dexcomoneplus.parse.OnePlusBackFillControlRx
import app.aaps.plugins.dexcomoneplus.parse.OnePlusBackFillStream
import app.aaps.plugins.dexcomoneplus.parse.OnePlusBackFillTx
import kotlin.time.Clock

/**
 * Short history pull after session up — G7/ONE+ BackFillTxMessage2 path.
 *
 * Sequence: enable ProbablyBackfill notify → write Control 0x59 window →
 * collect stream → decode → [onGlucose].
 *
 * ⚠️ ASYNC IMPACT: Blocks bleExecutor (Control + backfill awaits). [shouldContinue]
 * false / GATT disconnect must unblock both queues.
 *
 * Provenance: Ob1G5StateMachine.backFillIfNeeded / monitorBackFill / BackFillStream
 * at pin `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
class OnePlusBackfillSession(
    private val gatt: OnePlusGattClient,
    private val onGlucose: (OnePlusGlucoseSample) -> Unit = {},
    private val onError: (String, Boolean) -> Unit = { _, _ -> },
) {

    /**
     * @param currentDexTimeSeconds from TransmitterTime (required > 0)
     * @return number of samples delivered
     */
    fun runOnce(
        currentDexTimeSeconds: Int,
        shouldContinue: () -> Boolean,
        lookbackMs: Long = DEFAULT_LOOKBACK_MS,
    ): Int {
        if (!gatt.isConnected() || !shouldContinue()) return 0
        if (currentDexTimeSeconds < 1) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: backfill skipped — no dexTime",
            )
            return 0
        }

        val lookbackSec = (lookbackMs / 1000L).toInt().coerceAtLeast(1)
        val endDex = currentDexTimeSeconds
        val startDex = (currentDexTimeSeconds - lookbackSec).coerceAtLeast(1)
        if (startDex >= endDex) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: backfill skipped — empty window",
            )
            return 0
        }

        try {
            gatt.enableBackfillNotifications()
        } catch (t: Throwable) {
            val msg = t.message ?: "ONEPLUS_BACKFILL_CCCD_FAILED"
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: $msg", t)
            onError(msg, false)
            return 0
        }
        if (!onePlusSleep(CCCD_SETTLE_MS)) return 0

        val tx = OnePlusBackFillTx.build(startDex, endDex)
        try {
            gatt.writeControl(tx)
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: wrote BackFillTx2 opcode=0x59 " +
                    "start=$startDex end=$endDex",
            )
        } catch (t: Throwable) {
            val msg = t.message ?: "ONEPLUS_BACKFILL_WRITE_FAILED"
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: $msg", t)
            onError(msg, false)
            return 0
        }

        // Optional Control ACK (same opcode 0x59).
        val ack = gatt.awaitControlNotify(ACK_TIMEOUT_MS)
        if (ack != null && OnePlusBackFillControlRx.isAck(ack)) {
            OnePlusLog.d("${OnePlusLogMarkers.SESSION}: BackFillControlRx ACK")
        } else if (ack != null) {
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: backfill Control notify non-ACK op=0x" +
                    (ack[0].toInt() and 0xff).toString(16),
            )
        }

        val stream = OnePlusBackFillStream()
        val deadline = Clock.System.now().toEpochMilliseconds() + COLLECT_TIMEOUT_MS
        var lastPacketMs = 0L
        while (shouldContinue() && gatt.isConnected() && Clock.System.now().toEpochMilliseconds() < deadline) {
            val wait = if (stream.hasData()) QUIET_TIMEOUT_MS else 1_000L
            val packet = gatt.awaitBackfillNotify(wait)
            if (!shouldContinue()) break
            if (packet == null) {
                if (stream.hasData() && lastPacketMs > 0L &&
                    Clock.System.now().toEpochMilliseconds() - lastPacketMs >= QUIET_TIMEOUT_MS
                ) {
                    break
                }
                continue
            }
            stream.pushG7(packet)
            lastPacketMs = Clock.System.now().toEpochMilliseconds()
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: backfill chunk ${packet.size}b total=${stream.byteCount()}",
            )
        }

        val nowMs = Clock.System.now().toEpochMilliseconds()
        val samples = stream.decode(currentDexTimeSeconds, nowMs)
        for (sample in samples) {
            OnePlusLog.d(
                "${OnePlusLogMarkers.BG}: backfill mgdl=${sample.mgdl} ts=${sample.timestampMs}",
            )
            onGlucose(sample)
        }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: backfill done samples=${samples.size} bytes=${stream.byteCount()}",
        )
        return samples.size
    }

    companion object {
        const val CCCD_SETTLE_MS: Long = 200L
        const val ACK_TIMEOUT_MS: Long = 5_000L
        const val COLLECT_TIMEOUT_MS: Long = 15_000L
        const val QUIET_TIMEOUT_MS: Long = 3_000L
        /** Short window (Ob1 G5 default 3 h; G7 allows 24 h). */
        const val DEFAULT_LOOKBACK_MS: Long = 3L * 60L * 60L * 1000L
    }
}
