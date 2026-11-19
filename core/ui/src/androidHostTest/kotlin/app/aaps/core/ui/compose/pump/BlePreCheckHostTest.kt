package app.aaps.core.ui.compose.pump

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.app.ActivityOptionsCompat
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BlePreCheckResult
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Bluetooth check every pump runs before it talks to the pump. When Bluetooth is off it asks the system to turn it
 * on, and goes on when the user said yes; the message is only for a no, or for a platform that cannot ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlePreCheckHostTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    /** What the system requests got, and the answer the "user" gives. */
    private val launched = mutableListOf<Intent>()
    private var answer = Activity.RESULT_OK

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launched += input as Intent
                dispatchResult(requestCode, answer, null)
            }
        }
    }

    private var ready = 0
    private var failed = 0

    private fun show(vararg results: BlePreCheckResult) {
        val blePreCheck = mock<BlePreCheck> { on { checkBleReady() } doReturnConsecutively results.toList() }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                BlePreCheckHost(blePreCheck, onReady = { ready++ }, onFailed = { failed++ })
            }
        }
    }

    @Test
    fun `Bluetooth on is ready at once and asks nothing`() {
        show(BlePreCheckResult.READY)

        compose.waitUntil(TIMEOUT) { ready == 1 }
        assertThat(launched).isEmpty()
    }

    @Test
    fun `Bluetooth off asks the system to turn it on and goes on when the user says yes`() {
        show(BlePreCheckResult.BLE_NOT_ENABLED, BlePreCheckResult.READY)

        compose.waitUntil(TIMEOUT) { ready == 1 }
        assertThat(launched.single().action).isEqualTo(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        assertThat(failed).isEqualTo(0)
    }

    @Test
    fun `Bluetooth off and the user says no shows the message`() {
        answer = Activity.RESULT_CANCELED
        show(BlePreCheckResult.BLE_NOT_ENABLED)

        compose.waitUntil(TIMEOUT) { launched.isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.ble_not_enabled)).assertIsDisplayed()
        assertThat(ready).isEqualTo(0)
    }

    @Test
    fun `Bluetooth still off after a yes shows the message and does not ask again`() {
        show(BlePreCheckResult.BLE_NOT_ENABLED, BlePreCheckResult.BLE_NOT_ENABLED)

        compose.waitUntil(TIMEOUT) { launched.isNotEmpty() }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(context.getString(R.string.ble_not_enabled))).fetchSemanticsNodes().isNotEmpty() }
        assertThat(launched).hasSize(1)
    }

    private companion object {

        const val TIMEOUT = 5_000L
    }
}
