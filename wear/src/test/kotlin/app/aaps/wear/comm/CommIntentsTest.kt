package app.aaps.wear.comm

import app.aaps.core.interfaces.rx.weardata.EventData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers the two small wear→mobile [android.content.Intent] wrappers: action, target service and the
 * serialized command payload carried in the extras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class CommIntentsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `IntentWearToMobile carries the raw command in the action-data extra`() {
        val intent = IntentWearToMobile(context, "the-command")

        assertThat(intent.action).isEqualTo(DataLayerListenerServiceWear.INTENT_WEAR_TO_MOBILE)
        assertThat(intent.component?.className).isEqualTo(DataLayerListenerServiceWear::class.java.name)
        assertThat(intent.getStringExtra(DataLayerListenerServiceWear.KEY_ACTION_DATA)).isEqualTo("the-command")
    }

    @Test
    fun `IntentWearToMobile serializes an EventData command`() {
        val command = EventData.ActionPing(1_000L)

        val intent = IntentWearToMobile(context, command)

        assertThat(intent.action).isEqualTo(DataLayerListenerServiceWear.INTENT_WEAR_TO_MOBILE)
        assertThat(intent.getStringExtra(DataLayerListenerServiceWear.KEY_ACTION_DATA)).isEqualTo(command.serialize())
    }

    @Test
    fun `IntentCancelNotification targets the listener service with the cancel action`() {
        val intent = IntentCancelNotification(context)

        assertThat(intent.action).isEqualTo(DataLayerListenerServiceWear.INTENT_CANCEL_NOTIFICATION)
        assertThat(intent.component?.className).isEqualTo(DataLayerListenerServiceWear::class.java.name)
    }
}
