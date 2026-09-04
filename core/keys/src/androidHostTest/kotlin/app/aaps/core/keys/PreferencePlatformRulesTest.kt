package app.aaps.core.keys

import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.SyncDirection
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rules a `platforms` restriction has to obey.
 *
 * `PreferenceKey.platforms` hides a row from the settings screen, the search index and the
 * hidden-value check at once. That is the point of it, and also why it is worth a guard: getting it
 * wrong hides a working control and there is nothing on screen to say so.
 */
class PreferencePlatformRulesTest {

    private fun allKeys(): List<PreferenceKey> =
        BooleanKey.entries + IntKey.entries + StringKey.entries + DoubleKey.entries + UnitDoubleKey.entries

    /**
     * A synced key must not be restricted, and this is the trap the whole rule exists for.
     *
     * A `Bidirectional` key shown on a client is not configuring the client - it is configuring the
     * **master**, over the Client-Control channel. `AutomationLocation` is exactly that: the
     * GPS/NETWORK/PASSIVE choice is read only by Android's `LocationService`, so it looks like a row
     * that does nothing on iOS, and it is in fact a working remote control for the Android master.
     * Restricting it by the platform doing the *displaying* would take that away.
     *
     * So the question a `platforms` restriction answers is "can the platform that must honour this
     * honour it", and for a synced key that platform is not the one drawing the row.
     */
    @Test
    fun `a synced key is never restricted to a platform`() {
        val offenders = allKeys()
            .filter { it.sync?.direction == SyncDirection.Bidirectional }
            .filter { it.platforms != AppPlatform.ALL }
            .map { it.key }

        assertThat(offenders).isEmpty()
    }

    /** A restriction naming no platform at all would hide the row everywhere, silently. */
    @Test
    fun `a restriction always names at least one platform`() {
        val empty = allKeys().filter { it.platforms.isEmpty() }.map { it.key }

        assertThat(empty).isEmpty()
    }

    /** The key this was built for, pinned so the sweep is not quietly undone. */
    @Test
    fun `the android notification policy switch is Android only`() {
        assertThat(BooleanKey.AlertUrgentAsAndroidNotification.platforms).isEqualTo(AppPlatform.ANDROID_ONLY)
    }

    /** Everything else is unrestricted until someone shows a platform cannot honour it. */
    @Test
    fun `restrictions are the exception`() {
        val restricted = allKeys().filter { it.platforms != AppPlatform.ALL }.map { it.key }

        assertThat(restricted).containsExactly("raise_urgent_alarms_as_android_notification")
    }
}
