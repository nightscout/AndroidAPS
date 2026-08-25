package app.aaps.wear.comm

import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [ExceptionHandlerWear]: after [ExceptionHandlerWear.register] it installs itself as the
 * default uncaught-exception handler, and when an exception is dispatched it forwards a serialized
 * [EventData.WearException] to the phone via [RxBus] before delegating to the previous handler.
 * Robolectric supplies the `Build.*` fields the exception envelope reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class ExceptionHandlerWearTest {

    private val rxBus: RxBus = mock()

    @Test
    fun `register installs a handler that reports the exception upstream then delegates`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var delegatedTo = false
        // Install a known previous handler so registration captures it (and we can assert delegation).
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegatedTo = true }
        try {
            ExceptionHandlerWear(rxBus).register()
            val installed = Thread.getDefaultUncaughtExceptionHandler()

            installed.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            val captor = argumentCaptor<Event>()
            verify(rxBus).send(captor.capture())
            val sent = captor.firstValue
            assertThat(sent).isInstanceOf(EventWearToMobile::class.java)
            assertThat((sent as EventWearToMobile).payload).isInstanceOf(EventData.WearException::class.java)
            assertThat(delegatedTo).isTrue()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
