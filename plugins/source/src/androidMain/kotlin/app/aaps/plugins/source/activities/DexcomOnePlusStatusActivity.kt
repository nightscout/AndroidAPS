package app.aaps.plugins.source.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.source.R
import app.aaps.plugins.source.compose.CgmScaffold
import app.aaps.plugins.source.compose.DexcomOnePlusStatusActions
import app.aaps.plugins.source.compose.DexcomOnePlusStatusOverview
import app.aaps.plugins.source.compose.DexcomOnePlusStatusViewModel
import dev.zacsweers.metro.Inject

/**
 * Daily status of the native Dexcom ONE+ / G7 source, as a window of its own.
 *
 * The screen itself is [DexcomOnePlusStatusOverview]; the plugin tab shows the same one behind its
 * sensor button. This Activity stays for the places that cannot open the plugin tab: the session
 * notification, and the start and warm-up screens, which are Activities themselves. The status is
 * no longer an entry of the settings, so it is not reachable there twice.
 */
class DexcomOnePlusStatusActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, calibration, pre-soak, switch). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    val viewModel: DexcomOnePlusStatusViewModel = metroViewModel()
                    val actions = remember {
                        DexcomOnePlusStatusActions(
                            onOpenStart = { startActivity(Intent(this, DexcomOnePlusStartActivity::class.java)) },
                            onOpenWarmup = { startActivity(Intent(this, DexcomOnePlusWarmupActivity::class.java)) },
                            // The full driver log window, as before.
                            onOpenLog = { startActivity(CgmDriverLogActivity.intent(this, setOf(OnePlusLogMarkers.TAG))) },
                        )
                    }
                    CgmScaffold(
                        title = stringResource(R.string.dexcom_oneplus_status_title),
                        onNavigate = { finish() },
                        actions = {
                            IconButton(onClick = actions.onOpenLog) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.cgm_driver_log_open),
                                )
                            }
                        },
                    ) {
                        DexcomOnePlusStatusOverview(viewModel = viewModel, actions = actions)
                    }
                }
            }
        }
    }
}
