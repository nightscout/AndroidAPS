package app.aaps.plugins.source

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.session.OnePlusAuthRefusalPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The ongoing notification during RECONNECTING. The wait between two readings is normal and must
 * not post a notification after every reading; a missed reading, a stale sensor or an error must.
 */
class DexcomOnePlusReconnectNoticeTest {

    private val now = 1_800_000_000_000L

    private fun reconnecting(
        message: String?,
        silenceMinutes: Long? = null,
        staleMac: Boolean = false,
        warmupEndsAt: Long? = null,
    ) = OnePlusWarmupState(
        phase = OnePlusWarmupState.Phase.RECONNECTING,
        endsAtEpochMs = warmupEndsAt,
        message = message,
        advSilenceMinutes = silenceMinutes,
        staleMacSuspected = staleMac,
    )

    @Test
    fun `the wait between two readings shows nothing`() {
        for (minutes in 0L..5L) {
            assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_adv", minutes), now))
                .isEqualTo(DexcomOnePlusReconnectNotice.HIDE)
        }
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("egv_loop_exit"), now)).isEqualTo(DexcomOnePlusReconnectNotice.HIDE)
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_next_adv"), now)).isEqualTo(DexcomOnePlusReconnectNotice.HIDE)
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("connect_attempt_1"), now)).isEqualTo(DexcomOnePlusReconnectNotice.HIDE)
    }

    @Test
    fun `a missed reading says there is no signal`() {
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_adv", 6L), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.NO_SIGNAL)
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_adv", 25L), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.NO_SIGNAL)
    }

    @Test
    fun `a stale sensor address is always shown`() {
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_adv", 2L, staleMac = true), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.STALE_MAC)
    }

    @Test
    fun `an error or a refusal pause is shown as reconnecting`() {
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("ONEPLUS_GATT_STATUS_133"), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.RECONNECTING)
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting(OnePlusAuthRefusalPolicy.MESSAGE_PAUSED), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.RECONNECTING)
    }

    @Test
    fun `during warm-up the wait keeps the countdown`() {
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_adv", 2L, warmupEndsAt = now + 10 * 60_000L), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.WARMUP_COUNTDOWN)
        // An end in the past is no warm-up any more.
        assertThat(DexcomOnePlusReconnectNotice.of(reconnecting("waiting_for_adv", 2L, warmupEndsAt = now - 1L), now))
            .isEqualTo(DexcomOnePlusReconnectNotice.HIDE)
    }
}
