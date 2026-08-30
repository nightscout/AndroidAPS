package app.aaps.e2e

import android.content.Intent
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
        bringAppToForeground()
        // On the main thread on purpose: runAlarm takes the direct-launch path only from there, and the
        // off-main path posts a full screen intent instead, which a test cannot tap reliably.
        instrumentation.runOnMainSync {
            uiInteraction.runAlarm(status = "member injector check", title = "Metro alarm")
        }

        assertThat(device.wait(Until.findObject(By.textContains("Metro alarm")), TIMEOUT)).isNotNull()
        assertThat(device.wait(Until.findObject(By.textContains("member injector check")), TIMEOUT)).isNotNull()

        device.pressBack()
    }

    /**
     * `runAlarm` only launches the activity directly when the app is in the foreground; from the
     * background it posts a full screen intent instead, which is correct behaviour and invisible to
     * `By.textContains`. Running alone the app happened to be foreground already, so this test passed
     * on its own and failed in the shard after the earlier tests had left it backgrounded - the log
     * line then reads `runAlarm (background via FSI)` rather than `(foreground direct)`.
     */
    private fun bringAppToForeground() {
        val context = instrumentation.targetContext
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("No launch intent for ${context.packageName}")
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertThat(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), TIMEOUT)).isTrue()
    }

    private companion object {

        const val TIMEOUT = 20_000L
    }
}
