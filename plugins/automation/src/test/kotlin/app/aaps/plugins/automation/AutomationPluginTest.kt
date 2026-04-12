package app.aaps.plugins.automation

import android.Manifest
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.ui.TimerUtil
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AutomationPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var loop: Loop
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var timerUtil: TimerUtil
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    private lateinit var automationPlugin: AutomationPlugin

    @BeforeEach fun prepare() {
        automationPlugin = AutomationPlugin(
            injector, aapsLogger, rh, preferences, context, fabricPrivacy, loop, rxBus, constraintChecker,
            aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin, timerUtil, receiverStatusStore
        )
    }

    @Test
    fun `requiredPermissions should include location permissions`() {
        val allPermissions = automationPlugin.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).contains(Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(allPermissions).contains(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertThat(allPermissions).contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
}
