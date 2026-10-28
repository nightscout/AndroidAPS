package app.aaps.plugins.source.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverReal
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.setContext
import app.aaps.plugins.source.compose.DexcomOnePlusWarmupScreen
import dev.zacsweers.metro.Inject
import java.text.DateFormat
import java.util.Date

/**
 * The Android window around the warm-up screen, [DexcomOnePlusWarmupScreen].
 *
 * What stays here is what needs Android: the driver log screen, the start and status Activities to
 * open, the `Context` the driver and the slot store need, and the phone's own time format.
 */
class DexcomOnePlusWarmupActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, pre-soak, switch). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    DexcomOnePlusWarmupScreen(
                        storedSession = {
                            OnePlusSensorStore(
                                applicationContext,
                                OnePlusCgmDrivers.storeNamespace(SensorSlot.PRODUCTION),
                            ).load()
                        },
                        onBack = { finish() },
                        onRetry = {
                            val driver = OnePlusCgmDrivers.default()
                            driver.setContext(applicationContext)
                            (driver as? OnePlusCgmDriverReal)?.resumeStoredSession()
                        },
                        onOpenLog = {
                            startActivity(
                                Intent(this, CgmDriverLogActivity::class.java)
                            )
                        },
                        onOpenStart = {
                            startActivity(Intent(this, DexcomOnePlusStartActivity::class.java))
                        },
                        onOpenStatus = {
                            // CLEAR_TOP so coming here from the status screen returns to that
                            // instance instead of stacking a second one on every round trip.
                            startActivity(
                                Intent(this, DexcomOnePlusStatusActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        },
                        formatTime = { epochMs -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs)) },
                    )
                }
            }
        }
    }
}
