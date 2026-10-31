package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemProfileId
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanBudget
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusBleSessionCyclePolicyTest {

    @Test
    fun `successful collection waits for advertisement and suppresses SessionStart`() {
        val reconnectAttempt = OnePlusBleSessionCyclePolicy.POST_COLLECTION_RECONNECT_ATTEMPT

        assertThat(
            OnePlusBleSessionCyclePolicy.waitForAdvertisementAfterExit(
                sessionProvedHealthy = true,
            ),
        ).isTrue()
        assertThat(
            OnePlusSessionStartPolicy.wantSessionStartOnAttempt(
                requestNewSensorStart = true,
                attempt = reconnectAttempt,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.applyFailureBudget(
                preparedPostCollectionAdvertisement = true,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.requireFreshAdvertisementBeforeReconnect(
                persistentAdvertisementMode = true,
            ),
        ).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.allowConnection(
                restoredSessionMode = true,
                advertisementFresh = false,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.allowConnection(
                restoredSessionMode = true,
                advertisementFresh = true,
            ),
        ).isTrue()
    }

    @Test
    fun `a restored session can leave the persistent wait through the blind-connect escape`() {
        val silence = OnePlusBleSessionCyclePolicy.BLIND_CONNECT_AFTER_ADV_SILENCE_MS

        // Before the escape delay the restored session still refuses an ADV-less connect…
        assertThat(OnePlusBleSessionCyclePolicy.authorizeBlindConnect(silence - 1)).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.allowConnection(
                restoredSessionMode = true,
                advertisementFresh = false,
                blindFallbackAuthorized = false,
            ),
        ).isFalse()

        // …and once the silence is long enough it may connect without a sighting. Without this the
        // wait is unbounded (field log 2026-08-03: 18 min, no glucose, no error, no retry).
        assertThat(OnePlusBleSessionCyclePolicy.authorizeBlindConnect(silence)).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.allowConnection(
                restoredSessionMode = true,
                advertisementFresh = false,
                blindFallbackAuthorized = true,
            ),
        ).isTrue()
    }

    @Test
    fun `stale MAC is suspected from silence alone — a foreign ONE+ is not required`() {
        val threshold = OnePlusBleSessionCyclePolicy.STALE_MAC_SUSPICION_AFTER_MS

        assertThat(
            OnePlusBleSessionCyclePolicy.suspectStaleMac(continuousAdvSilenceMs = threshold, foreignSightings = 0),
        ).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.suspectStaleMac(continuousAdvSilenceMs = threshold - 1, foreignSightings = 0),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.suspectStaleMac(continuousAdvSilenceMs = threshold - 1, foreignSightings = 1),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.suspectStaleMac(continuousAdvSilenceMs = threshold, foreignSightings = 1),
        ).isTrue()
    }

    @Test
    fun `the wait reports whole minutes of silence`() {
        assertThat(OnePlusBleSessionCyclePolicy.advSilenceMinutes(-5L)).isEqualTo(0L)
        assertThat(OnePlusBleSessionCyclePolicy.advSilenceMinutes(59_999L)).isEqualTo(0L)
        assertThat(OnePlusBleSessionCyclePolicy.advSilenceMinutes(60_000L)).isEqualTo(1L)
        assertThat(OnePlusBleSessionCyclePolicy.advSilenceMinutes(11L * 60_000L)).isEqualTo(11L)
    }

    @Test
    fun `blind-connect escape fires before the stale-MAC suspicion is conclusive`() {
        assertThat(OnePlusBleSessionCyclePolicy.BLIND_CONNECT_AFTER_ADV_SILENCE_MS)
            .isLessThan(OnePlusBleSessionCyclePolicy.STALE_MAC_SUSPICION_AFTER_MS)
    }

    @Test
    fun `warm-up Control traffic proves the session so a duty cycle does not burn the retry budget`() {
        // The reported field failure: a warming sensor never sends usable glucose, so every duty
        // cycle used the bounded budget and warm-up always ended in FAILED.
        assertThat(
            OnePlusBleSessionCyclePolicy.controlTrafficProvesSession(OnePlusWarmupState.Phase.WARMING),
        ).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.controlTrafficProvesSession(OnePlusWarmupState.Phase.READY),
        ).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.waitForAdvertisementAfterExit(
                sessionProvedHealthy = OnePlusBleSessionCyclePolicy.controlTrafficProvesSession(
                    OnePlusWarmupState.Phase.WARMING,
                ),
            ),
        ).isTrue()

        // A stopped / expired / failed sensor keeps the bounded budget: waiting for its advertisement
        // would hide a real failure.
        listOf(
            OnePlusWarmupState.Phase.FAILED,
            OnePlusWarmupState.Phase.IDLE,
            OnePlusWarmupState.Phase.PAIRING,
            OnePlusWarmupState.Phase.CONNECTING,
            OnePlusWarmupState.Phase.RECONNECTING,
        ).forEach { phase ->
            assertThat(OnePlusBleSessionCyclePolicy.controlTrafficProvesSession(phase)).isFalse()
        }
    }

    @Test
    fun `a session with proven Control traffic recovers from an exhausted retry budget`() {
        assertThat(
            OnePlusBleSessionCyclePolicy.recoverExhaustedBudgetWithPersistentWait(
                sessionEverProvedControlChannel = true,
            ),
        ).isTrue()
    }

    @Test
    fun `a sensor that never sent Control traffic still ends in the terminal failure`() {
        // Wrong PIN or wrong sensor — but also the case that made a working pairing impossible:
        // a sensor with no session started authenticates fully, then hangs up on the first glucose
        // request. Recovering there would loop for ever, and SessionStart (attempt 0 only) could
        // never be re-issued. The terminal failure is what lets the user start the sensor again.
        assertThat(
            OnePlusBleSessionCyclePolicy.recoverExhaustedBudgetWithPersistentWait(
                sessionEverProvedControlChannel = false,
            ),
        ).isFalse()
    }

    @Test
    fun `the warm-up deadline survives the connection phases and is dropped when warm-up ends`() {
        val endsAt = 1_700_000_000_000L

        // Learned from a WARMING packet…
        assertThat(
            OnePlusBleSessionCyclePolicy.warmupDeadlineAfter(
                previousEndsAtMs = null,
                state = OnePlusWarmupState(
                    phase = OnePlusWarmupState.Phase.WARMING,
                    endsAtEpochMs = endsAt,
                ),
            ),
        ).isEqualTo(endsAt)

        // …kept while the link is re-established (the UI countdown used to blank out here)…
        listOf(
            OnePlusWarmupState.Phase.CONNECTING,
            OnePlusWarmupState.Phase.RECONNECTING,
            OnePlusWarmupState.Phase.PAIRING,
        ).forEach { phase ->
            assertThat(
                OnePlusBleSessionCyclePolicy.warmupDeadlineAfter(
                    previousEndsAtMs = endsAt,
                    state = OnePlusWarmupState(phase = phase),
                ),
            ).isEqualTo(endsAt)
        }

        // …a WARMING packet without its own clock keeps the known deadline…
        assertThat(
            OnePlusBleSessionCyclePolicy.warmupDeadlineAfter(
                previousEndsAtMs = endsAt,
                state = OnePlusWarmupState(phase = OnePlusWarmupState.Phase.WARMING),
            ),
        ).isEqualTo(endsAt)

        // …and it is dropped once warm-up is over, stopped or failed.
        listOf(
            OnePlusWarmupState.Phase.READY,
            OnePlusWarmupState.Phase.IDLE,
            OnePlusWarmupState.Phase.FAILED,
        ).forEach { phase ->
            assertThat(
                OnePlusBleSessionCyclePolicy.warmupDeadlineAfter(
                    previousEndsAtMs = endsAt,
                    state = OnePlusWarmupState(phase = phase),
                ),
            ).isNull()
        }
    }

    @Test
    fun `exit before any control traffic and unprepared retry retain failure budget`() {
        assertThat(
            OnePlusBleSessionCyclePolicy.waitForAdvertisementAfterExit(
                sessionProvedHealthy = false,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.applyFailureBudget(
                preparedPostCollectionAdvertisement = false,
            ),
        ).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.requireFreshAdvertisementBeforeReconnect(
                persistentAdvertisementMode = false,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.allowConnection(
                restoredSessionMode = false,
                advertisementFresh = false,
            ),
        ).isTrue()
    }

    @Test
    fun `persistent failure raises the strategy index instead of pinning it at 1`() {
        assertThat(OnePlusBleSessionCyclePolicy.nextPersistentAttempt(1)).isEqualTo(2)
        assertThat(OnePlusBleSessionCyclePolicy.nextPersistentAttempt(2)).isEqualTo(3)
    }

    @Test
    fun `Samsung still parks autoConnect on attempt 1 when there is no fresh ADV`() {
        // SamsungDefault.autoConnectFromAttempt = 0. This is the field-proven park; do not change it.
        assertThat(
            OnePlusBleSessionCyclePolicy.useAutoConnect(
                advFresh = false,
                attempt = 1,
                autoConnectFromAttempt = 0,
            ),
        ).isTrue()
        assertThat(
            OnePlusBleSessionCyclePolicy.useAutoConnect(
                advFresh = true,
                attempt = 1,
                autoConnectFromAttempt = 0,
            ),
        ).isFalse()
    }

    @Test
    fun `Generic reaches autoConnect on attempt 2, not on the pinned attempt 1`() {
        assertThat(
            OnePlusBleSessionCyclePolicy.useAutoConnect(
                advFresh = false,
                attempt = 1,
                autoConnectFromAttempt = 2,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.useAutoConnect(
                advFresh = false,
                attempt = 2,
                autoConnectFromAttempt = 2,
            ),
        ).isTrue()
    }

    @Test
    fun `Generic skips the ADV wait after a failed hard connect so autoConnect can run now`() {
        assertThat(
            OnePlusBleSessionCyclePolicy.skipAdvWaitAfterFailedConnect(
                lastConnectUsedAutoConnect = false,
                nextAttempt = 2,
                autoConnectFromAttempt = 2,
            ),
        ).isTrue()
    }

    @Test
    fun `Samsung does not skip the ADV wait after a failed autoConnect — no GATT storm`() {
        assertThat(
            OnePlusBleSessionCyclePolicy.skipAdvWaitAfterFailedConnect(
                lastConnectUsedAutoConnect = true,
                nextAttempt = 2,
                autoConnectFromAttempt = 0,
            ),
        ).isFalse()
    }

    @Test
    fun `a second blind connect waits a full silence window after the last one`() {
        val silence = OnePlusBleSessionCyclePolicy.BLIND_CONNECT_AFTER_ADV_SILENCE_MS

        assertThat(
            OnePlusBleSessionCyclePolicy.authorizeBlindConnect(
                continuousAdvSilenceMs = silence,
                msSinceLastBlindConnect = 30_000L,
            ),
        ).isFalse()
        assertThat(
            OnePlusBleSessionCyclePolicy.authorizeBlindConnect(
                continuousAdvSilenceMs = silence + silence,
                msSinceLastBlindConnect = silence,
            ),
        ).isTrue()
    }

    @Test
    fun `the persistent ADV wait stays inside the platform scan quota with both slots running`() {
        val cycleMs = OnePlusBleSessionCyclePolicy.PERSISTENT_ADV_SCAN_MS +
            OnePlusBleSessionCyclePolicy.ADV_WAIT_RESTART_DELAY_MS
        val startsPerWindowPerSlot = OnePlusScanBudget.WINDOW_MS.toDouble() / cycleMs
        val bothSlots = 2 * startsPerWindowPerSlot

        // The regression this guards: the wait used OemDeviceProfile.preConnectScanMs (3 s on
        // Generic, 2 s on Pixel) plus a 250 ms pause, i.e. a 3.25 s / 2.25 s cycle. That is 18 and
        // 27 scan starts per minute against a platform allowance of 10 — a single slot was over
        // quota on its own, and the app throttled itself blind trying to compensate.
        assertThat(bothSlots).isLessThan(OnePlusScanBudget.MAX_STARTS_PER_WINDOW.toDouble())
        assertThat(startsPerWindowPerSlot).isLessThan(
            OnePlusScanBudget.MAX_STARTS_PER_WINDOW_PLATFORM.toDouble() / 2,
        )
    }

    /**
     * The busy-radio case: car Bluetooth, a headset playing audio, a crowded metro. Every connect
     * fails and no ADV is heard for an hour. The session must keep trying, and must not turn that
     * into a burst of connects. See the KDoc of [OnePlusBleSessionCyclePolicy].
     */
    @Test
    fun `an hour of failed connects on a busy radio never ends the session and never bursts connects`() {
        val hourMs = 60L * 60_000L
        val stepMs = 10_000L
        for (id in OemProfileId.entries) {
            val profile = DeviceProfileRegistry.byId(id)

            // Asking for more radio time than audio leaves us correlated with peer status 19.
            assertThat(profile.forceHighConnectionPriority).isFalse()

            // Never a terminal failure once the sensor has sent Control traffic.
            assertThat(OnePlusBleSessionCyclePolicy.recoverExhaustedBudgetWithPersistentWait(true)).isTrue()
            assertThat(OnePlusBleSessionCyclePolicy.applyFailureBudget(preparedPostCollectionAdvertisement = true)).isFalse()

            // A failed connect is retried without an ADV wait at most once in a row.
            var attempt = OnePlusBleSessionCyclePolicy.POST_COLLECTION_RECONNECT_ATTEMPT
            var lastUsedAutoConnect = false
            var skipsInARow = 0
            repeat(50) {
                val skip = OnePlusBleSessionCyclePolicy.skipAdvWaitAfterFailedConnect(
                    lastConnectUsedAutoConnect = lastUsedAutoConnect,
                    nextAttempt = OnePlusBleSessionCyclePolicy.nextPersistentAttempt(attempt),
                    autoConnectFromAttempt = profile.autoConnectFromAttempt,
                )
                skipsInARow = if (skip) skipsInARow + 1 else 0
                assertThat(skipsInARow).isAtMost(1)
                attempt = OnePlusBleSessionCyclePolicy.nextPersistentAttempt(attempt)
                lastUsedAutoConnect = OnePlusBleSessionCyclePolicy.useAutoConnect(
                    advFresh = false,
                    attempt = attempt,
                    autoConnectFromAttempt = profile.autoConnectFromAttempt,
                )
            }

            // Without any ADV, a connect is still tried once per silence window: never less, never more.
            val blindAt = mutableListOf<Long>()
            var t = 0L
            while (t <= hourMs) {
                val sinceBlind = blindAt.lastOrNull()?.let { t - it }
                if (OnePlusBleSessionCyclePolicy.authorizeBlindConnect(t, sinceBlind)) blindAt += t
                t += stepMs
            }
            val window = OnePlusBleSessionCyclePolicy.BLIND_CONNECT_AFTER_ADV_SILENCE_MS
            assertThat(blindAt.first()).isEqualTo(window)
            blindAt.zipWithNext { a, b -> assertThat(b - a).isEqualTo(window) }
            assertThat(blindAt.size.toLong()).isEqualTo(hourMs / window)
        }
    }

    @Test
    fun `the post-cycle guard outlasts a residual burst but never hides the next duty cycle`() {
        val guard = OnePlusBleSessionCyclePolicy.POST_CYCLE_ADV_GUARD_MS

        // Field log 2026-08-20: the residual advertisement arrived 127 ms after the cycle closed.
        assertThat(guard).isGreaterThan(1_000L)
        // A G7 duty cycle is ~5 min, and the blind-connect escape fires at 4 min; the guard must be
        // far below both so it can never swallow a genuine window or delay the escape.
        assertThat(guard).isLessThan(OnePlusBleSessionCyclePolicy.BLIND_CONNECT_AFTER_ADV_SILENCE_MS / 10)
    }
}
