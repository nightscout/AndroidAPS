package app.aaps.plugins.source

import android.content.Context
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Starting the keep-alive service must not crash the sensor session when the platform refuses it.
 *
 * `Libre3SessionService.start` wraps `startForegroundService` in `runCatching` and does not
 * rethrow. It hands the failure back, so the plugin can log it.
 */
class Libre3SessionServiceStartTest : TestBase() {

    @Mock lateinit var context: Context

    @Test
    fun `start does not throw when the platform refuses the service`() {
        whenever(context.startService(any())).thenThrow(SecurityException("blocked"))
        whenever(context.startForegroundService(any())).thenThrow(SecurityException("blocked"))

        val failure = Libre3SessionService.start(context)

        assertThat(failure).isInstanceOf(SecurityException::class.java)
    }
}
