package app.aaps.plugins.automation.services

import android.content.Context
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the Android implementation of [app.aaps.core.interfaces.location.LocationServiceController].
 *
 * Narrow on purpose: `ActivityCompat.checkSelfPermission` is a static call and the unit-test Android
 * jar answers it with 0, which happens to equal PERMISSION_GRANTED, so the permission-denied branch
 * cannot be reached from here. What is worth pinning is the part automation depends on - that start
 * binds the service and stop stops it, both naming the class that the manifest declares.
 */
class LocationServiceControllerImplTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var notificationHolder: NotificationHolder

    private lateinit var sut: LocationServiceControllerImpl

    @BeforeEach
    fun prepare() {
        sut = LocationServiceControllerImpl(context, notificationHolder)
    }

    @Test
    fun startBindsTheLocationService() {
        val issued = sut.startService()

        assertThat(issued).isTrue()
        val intent = argumentCaptor<android.content.Intent>()
        verify(context).bindService(intent.capture(), any<android.content.ServiceConnection>(), any<Int>())
        // Never startActivity: this runs from background contexts where Android blocks it.
        verify(context, never()).startActivity(anyOrNull())
    }

    @Test
    fun startFallsBackToForegroundServiceWhenBindingIsNotPossible() {
        // bindService throws from a broadcast-receiver context. The service then has to be started
        // directly, or the location trigger stops updating with nothing logged.
        whenever(context.bindService(anyOrNull(), any<android.content.ServiceConnection>(), any<Int>())).thenThrow(RuntimeException("cannot bind"))

        val issued = sut.startService()

        assertThat(issued).isTrue()
        verify(context).startForegroundService(anyOrNull())
    }

    @Test
    fun stopStopsTheService() {
        sut.stopService()

        verify(context).stopService(anyOrNull())
    }
}
