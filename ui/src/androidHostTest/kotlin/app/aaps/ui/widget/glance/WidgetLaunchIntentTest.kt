package app.aaps.ui.widget.glance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The widgets asked the package manager for the launch intent inside their composition, on the main thread, at every
 * redraw: a binder call, and the app did not answer (ANR, Crashlytics 2026-11). The lookup now runs off the thread
 * that asks for it.
 */
internal class WidgetLaunchIntentTest {

    @Test
    fun `the package manager is asked off the calling thread`() {
        val intent = mock<Intent>()
        var askedOn: Thread? = null
        val packageManager = mock<PackageManager> {
            on { getLaunchIntentForPackage(PACKAGE) } doAnswer {
                askedOn = Thread.currentThread()
                intent
            }
        }
        val context = mock<Context>()
        whenever(context.packageName).thenReturn(PACKAGE)
        whenever(context.packageManager).thenReturn(packageManager)

        val result = runBlocking { widgetLaunchIntent(context) }

        assertThat(result).isSameInstanceAs(intent)
        assertThat(askedOn).isNotNull()
        assertThat(askedOn).isNotSameInstanceAs(Thread.currentThread())
    }

    private companion object {

        const val PACKAGE = "info.nightscout.androidaps"
    }
}
