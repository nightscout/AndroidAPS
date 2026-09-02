package app.aaps.di.metro

import app.aaps.persistentNotification.DummyService
import app.aaps.plugins.automation.TimerReminderReceiver
import app.aaps.plugins.automation.services.LocationService
import app.aaps.receivers.AutoStartReceiver
import app.aaps.receivers.CarbSuggestionReceiver
import app.aaps.receivers.DataReceiver
import app.aaps.receivers.SmsReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every Android entry point that Metro injects must have a map entry.
 *
 * `MetroGraphs.injectMembers` looks the injector up by `target::class` - the RUNTIME class. A missing
 * entry is not a build error: the receiver is constructed by Android, `injectMetroMembers` throws
 * "No Metro binding for ...", and the failure only happens when the broadcast actually arrives. For a
 * BG source receiver that means a reading is dropped in the field.
 *
 * [SmsReceiver] is the case worth spelling out. It subclasses [DataReceiver] and adds no fields, so it
 * is tempting to assume the parent's entry covers it. It does not - the key is the runtime class.
 */
class ReceiverInjectorsTest {

    private val injectors get() = testRoot().contributedMemberInjectors

    @Test
    fun `the three app receivers have injectors`() {
        assertThat(injectors.keys).containsAtLeast(
            AutoStartReceiver::class,
            DataReceiver::class,
            SmsReceiver::class
        )
    }

    @Test
    fun `the persistent notification service and receiver have injectors`() {
        // DummyService is a Service, not a receiver. It reaches the same class-keyed map through
        // `MetroService`, and a missing entry would only show as a crash when the foreground
        // notification starts.
        assertThat(injectors.keys).containsAtLeast(DummyService::class, CarbSuggestionReceiver::class)
    }

    /**
     * The automation module reaches the same map from its own module.
     *
     * It used to own a graph extension with a map of its own; these two now contribute into the
     * root map like every pump driver does. A missing entry here is a crash when a reminder fires
     * or a location trigger starts its service, which is a long way from this file.
     */
    @Test
    fun `the automation receiver and service reach the same map`() {
        assertThat(injectors.keys).containsAtLeast(TimerReminderReceiver::class, LocationService::class)
    }

    @Test
    fun `a subclass gets its own entry, not the parent's`() {
        assertThat(injectors[SmsReceiver::class]).isNotNull()
        assertThat(injectors[SmsReceiver::class]).isNotSameInstanceAs(injectors[DataReceiver::class])
    }
}
