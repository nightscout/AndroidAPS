package app.aaps.plugins.dexcomoneplus.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusSessionStartPolicyTest {

    @Test
    fun attachOnly_whenSessionAlreadyInProgress() {
        assertThat(
            OnePlusSessionStartPolicy.decide(
                requestNewSensorStart = true,
                sessionAlreadyInProgress = true,
            ),
        ).isEqualTo(OnePlusSessionStartPolicy.Action.AttachOnly)

        assertThat(
            OnePlusSessionStartPolicy.decide(
                requestNewSensorStart = false,
                sessionAlreadyInProgress = true,
            ),
        ).isEqualTo(OnePlusSessionStartPolicy.Action.AttachOnly)
    }

    @Test
    fun sessionStart_onlyWhenIdleAndRequested() {
        assertThat(
            OnePlusSessionStartPolicy.decide(
                requestNewSensorStart = true,
                sessionAlreadyInProgress = false,
            ),
        ).isEqualTo(OnePlusSessionStartPolicy.Action.SessionStart)
    }

    @Test
    fun egvOnly_whenIdleAndNotRequested() {
        assertThat(
            OnePlusSessionStartPolicy.decide(
                requestNewSensorStart = false,
                sessionAlreadyInProgress = false,
            ),
        ).isEqualTo(OnePlusSessionStartPolicy.Action.EgvOnly)
    }

    @Test
    fun reconnectAttempts_neverRequestSessionStart() {
        assertThat(
            OnePlusSessionStartPolicy.wantSessionStartOnAttempt(
                requestNewSensorStart = true,
                attempt = 0,
            ),
        ).isTrue()
        assertThat(
            OnePlusSessionStartPolicy.wantSessionStartOnAttempt(
                requestNewSensorStart = true,
                attempt = 1,
            ),
        ).isFalse()
        assertThat(
            OnePlusSessionStartPolicy.wantSessionStartOnAttempt(
                requestNewSensorStart = false,
                attempt = 0,
            ),
        ).isFalse()
    }
}
