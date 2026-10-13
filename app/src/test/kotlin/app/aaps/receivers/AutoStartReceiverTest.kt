package app.aaps.receivers

import android.content.Intent
import app.aaps.plugins.main.general.persistentNotification.DummyServiceHelper
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutoStartReceiverTest : TestBaseWithProfile() {

    private lateinit var autoStartReceiver: AutoStartReceiver

    @Mock private lateinit var dummyServiceHelper: DummyServiceHelper

    @BeforeEach
    fun setUpMocks() {
        // Create an instance of the receiver and manually inject the mock dependency
        autoStartReceiver = AutoStartReceiver().also {
            it.dummyServiceHelper = dummyServiceHelper
        }
    }

    @Test
    fun `processIntent calls startService when intent action is ACTION_BOOT_COMPLETED`() {
        // Arrange
        // Create an intent with the specific action the receiver is listening for.
        val bootIntent: Intent = mock()
        whenever(bootIntent.action).thenReturn(Intent.ACTION_BOOT_COMPLETED)

        // Act
        // Call the method under test.
        autoStartReceiver.processIntent(context, bootIntent)

        // Assert
        // Verify that dummyServiceHelper.startService() was called exactly once with the correct context.
        verify(dummyServiceHelper).startService(context)
    }

    @Test
    fun `processIntent does NOT call startService for a different intent action`() {
        // Arrange
        // Create an intent with a different, irrelevant action.
        val otherIntent: Intent = mock()
        whenever(otherIntent.action).thenReturn("some.other.ACTION")

        // Act
        autoStartReceiver.processIntent(context, otherIntent)

        // Assert
        // Verify that dummyServiceHelper.startService() was never called.
        verify(dummyServiceHelper, never()).startService(context)
    }

    @Test
    fun `processIntent does NOT call startService when intent action is null`() {
        // Arrange
        // Create an intent with no action set.
        val nullActionIntent: Intent = mock()

        // Act
        autoStartReceiver.processIntent(context, nullActionIntent)

        // Assert
        // Verify that dummyServiceHelper.startService() was never called.
        verify(dummyServiceHelper, never()).startService(context)
    }
}
