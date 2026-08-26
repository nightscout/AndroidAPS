package app.aaps.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.aaps.HiltInstrumentedTest
import app.aaps.core.interfaces.ui.UiInteraction
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * `ErrorActivity` still gets its dependencies after moving to `MetroAppCompatActivity`.
 *
 * The activity is not exported, so `am start` cannot reach it - the only honest way in is the path the
 * app itself uses, [UiInteraction.runAlarm]. That is worth exercising for its own sake: it is how every
 * alarm in the app reaches the screen.
 *
 * What makes this a real injection test rather than a launch test: the activity reads
 * `intent.getStringExtra` into a field it renders, and logs through `aapsLogger`, which is an `@Inject`
 * field. Nothing appears on screen unless `MetroAppCompatActivity.onCreate` filled those fields first -
 * and if the member-injector entry were missing, that `check` throws by name instead.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ErrorActivityInjectionTest : HiltInstrumentedTest() {

    @Inject lateinit var uiInteraction: UiInteraction

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @Test
    fun runAlarm_bringsUpTheErrorScreen_withItsInjectedFields() {
        device.wakeUp()
        // On the main thread on purpose: runAlarm takes the direct-launch path only from there, and the
        // off-main path posts a full screen intent instead, which a test cannot tap reliably.
        instrumentation.runOnMainSync {
            uiInteraction.runAlarm(status = "member injector check", title = "Metro alarm")
        }

        assertThat(device.wait(Until.findObject(By.textContains("Metro alarm")), TIMEOUT)).isNotNull()
        assertThat(device.wait(Until.findObject(By.textContains("member injector check")), TIMEOUT)).isNotNull()

        device.pressBack()
    }

    private companion object {

        const val TIMEOUT = 20_000L
    }
}
