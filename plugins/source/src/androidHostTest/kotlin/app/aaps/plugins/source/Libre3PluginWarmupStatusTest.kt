package app.aaps.plugins.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The generic warm-up status is set together with the driver state, with no collector, so it works
 * while the plugin is off and needs no running scope.
 *
 * Robolectric, because a warm-up state also posts the status bar message.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [35])
class Libre3PluginWarmupStatusTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val plugin = Libre3NativePlugin(
        mock<ResourceHelper>(), AAPSLoggerTest(), mock<Preferences>(), mock<Config>(), mock<NotificationManager>(),
        context, mock<PersistenceLayer>(), mock<ActivePlugin>(),
    )

    @Test
    fun `the warm-up status follows the driver without a running collector`() {
        assertThat(plugin.warmupStatus.value).isNull()

        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.WARMING, remainingMs = 60_000L))

        assertThat(plugin.warmupStatus.value!!.phase).isEqualTo(CgmWarmupStatus.Phase.WARMING)
        assertThat(plugin.warmupStatus.value!!.remainingMs).isEqualTo(60_000L)
        assertThat(plugin.hasRunningWork()).isFalse()

        plugin.onWarmup(Libre3WarmupState(phase = Libre3WarmupState.Phase.READY))

        assertThat(plugin.warmupStatus.value).isNull()
    }
}
