package app.aaps.di.metro

import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The `:plugins:sync` entry points Android constructs must have a member injector entry.
 *
 * Same reason as [ReceiverInjectorsTest]: a missing entry is not a build error, and only shows when the
 * system creates the class. For the wear pieces that means the watch silently stops talking to the
 * phone, which is easy to miss.
 *
 * [DataLayerListenerServiceMobile] cannot use `MetroService` - it already extends
 * `WearableListenerService` - so it calls `injectMetroMembers` itself. That makes the entry easier to
 * forget than for a class whose base class does it.
 */
class SyncEntryPointInjectorsTest {

    private val injectors get() = testRoot().contributedMemberInjectors

    @Test
    fun `the wear entry points have injectors`() {
        assertThat(injectors.keys).containsAtLeast(
            WearDataReceiver::class,
            DataLayerListenerServiceMobile::class
        )
    }

    @Test
    fun `the nsclient service has an injector`() {
        assertThat(injectors.keys).contains(NSClientV3Service::class)
    }
}
