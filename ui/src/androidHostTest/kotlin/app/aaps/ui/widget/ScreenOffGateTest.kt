package app.aaps.ui.widget

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Widgets are not updated while no display is on, but the update must not be lost: the widgets show
 * "time ago", so the held back update has to run as soon as a display turns on (#5246).
 */
internal class ScreenOffGateTest {

    private val sut = ScreenOffGate()

    @Test
    fun `an update runs at once while a display is on`() {
        assertThat(sut.request(displayOn = true)).isTrue()
        assertThat(sut.displayTurnedOn()).isFalse()
    }

    @Test
    fun `an update while all displays are off runs when a display turns on`() {
        assertThat(sut.request(displayOn = false)).isFalse()
        assertThat(sut.request(displayOn = false)).isFalse()

        assertThat(sut.displayTurnedOn()).isTrue()
        // Many skipped updates give one update, and only once
        assertThat(sut.displayTurnedOn()).isFalse()
    }

    @Test
    fun `nothing runs when a display turns on and no update was held back`() {
        assertThat(sut.displayTurnedOn()).isFalse()
    }

    /** An update that ran with the display on makes the one held back before it unnecessary. */
    @Test
    fun `an update that ran clears the one held back`() {
        sut.request(displayOn = false)
        sut.request(displayOn = true)

        assertThat(sut.displayTurnedOn()).isFalse()
    }
}
