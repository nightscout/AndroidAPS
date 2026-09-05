package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.mutableStateMapOf
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.SyncDirection
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Every synced key must have a way to get an external change back onto the screen.
 *
 * The bug this exists for: `UnitDoubleKey.OverviewLowMark` is `Bidirectional`, so an edit on a client
 * reached the master and was stored - and the master's screen went on showing the old number. The
 * type dispatch that refreshes the on-screen cache handled Boolean, String and Int and simply had no
 * branch for it, and no `else`, so it fell through in silence. Confirmed on two devices: the master
 * logged `applied low_mark=81.07` while its own row still read 4.0 mmol/L.
 *
 * Nothing about adding `sync = SyncSpec(...)` to a key of an unhandled type fails - it compiles, it
 * syncs, it persists. Only this test notices.
 */
class SyncedKeyStateCoverageTest {

    /** The declared-synced keys, taken from the enums rather than from a running `Preferences`. */
    private fun syncedKeys(): List<NonPreferenceKey> =
        (BooleanKey.entries + StringKey.entries + IntKey.entries + DoubleKey.entries + UnitDoubleKey.entries)
            .filter { it.sync?.direction == SyncDirection.Bidirectional }

    /**
     * The updater is only built here, never collected, so the mocks are never called - what is being
     * asked is whether the `when` has a branch for the type at all.
     */
    private fun unhandled(): List<String> {
        val preferences = mock<Preferences>()
        val states = mutableStateMapOf<String, Any?>()
        return syncedKeys()
            .filter { sharedStateUpdaterFor(it, preferences, states) == null }
            .map { "${it.key} (${it::class.simpleName})" }
    }

    @Test
    fun `every synced key can be refreshed on screen`() {
        assertThat(unhandled()).isEmpty()
    }

    /** Guards the test itself: if this list were empty the one above would pass by saying nothing. */
    @Test
    fun `there are synced keys to check`() {
        assertThat(syncedKeys().size).isGreaterThan(20)
    }

    /** The key the bug was found on, named so a regression points straight at the report. */
    @Test
    fun `the low BG mark is refreshed on screen`() {
        val updater = sharedStateUpdaterFor(UnitDoubleKey.OverviewLowMark, mock(), mutableStateMapOf())

        assertThat(updater).isNotNull()
    }
}
