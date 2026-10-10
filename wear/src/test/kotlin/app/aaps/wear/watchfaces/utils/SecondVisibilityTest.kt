package app.aaps.wear.watchfaces.utils

import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rule that decides whether the seconds are drawn.
 *
 * It exists as a function because of what happened when it was a line inside the watch face: it read
 * the view's current visibility and combined it with the mode, which made it a latch. Hidden once by
 * an ambient frame, it could never restore anything, because the value it read was the value it had
 * just written. Called once per frame it locked the second hand off and the face was missing most of
 * the time on a Galaxy Watch 4.
 *
 * The property that matters here is therefore not the truth table - it is that **the answer depends
 * only on its inputs**, so calling it repeatedly cannot drift.
 */
class SecondVisibilityTest {

    @Test
    fun `shown when the zip asks for it and the moment allows it`() {
        assertThat(secondVisibility(declaredVisible = true, showSecond = true)).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `hidden while the watch is dozing`() {
        assertThat(secondVisibility(declaredVisible = true, showSecond = false)).isEqualTo(View.GONE)
    }

    @Test
    fun `hidden when the zip does not ask for it`() {
        assertThat(secondVisibility(declaredVisible = false, showSecond = true)).isEqualTo(View.GONE)
    }

    @Test
    fun `comes back after being hidden`() {
        // The regression, written as a test: hide it, then ask again with the same inputs that make it
        // visible. A rule that reads its own previous answer would stay hidden here.
        secondVisibility(declaredVisible = true, showSecond = false)

        assertThat(secondVisibility(declaredVisible = true, showSecond = true)).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `gives the same answer however often it is asked`() {
        val answers = (1..10).map { secondVisibility(declaredVisible = true, showSecond = true) }

        assertThat(answers.toSet()).containsExactly(View.VISIBLE)
    }

    // ---- the seconds are decided at the moment the frame is drawn ------------------------------

    @Test
    fun `leaves the seconds out when the watch dozed while the frame was being made`() {
        // The fault: a frame begun awake takes 200 ms to over a second, and the wrist can drop in
        // that time. It went out with its second hand on it, and because the runtime repaints
        // always-on only about once a minute, the hand sat there long after the screen went dark.
        assertThat(showSeconds(enabledByUser = true, interactive = true, renderOverride = false)).isFalse()
    }

    @Test
    fun `follows the watch mode when no frame is being drawn`() {
        // The live watch face never sets the override, so it must behave exactly as before
        assertThat(showSeconds(enabledByUser = true, interactive = true, renderOverride = null)).isTrue()
        assertThat(showSeconds(enabledByUser = true, interactive = false, renderOverride = null)).isFalse()
    }

    @Test
    fun `never shows seconds the wearer switched off`() {
        // Whatever the frame asks for, the wearer's own choice wins
        assertThat(showSeconds(enabledByUser = false, interactive = true, renderOverride = true)).isFalse()
        assertThat(showSeconds(enabledByUser = false, interactive = true, renderOverride = null)).isFalse()
    }
}
