package app.aaps.plugins.source.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.DexcomOnePlusPlugin
import app.aaps.plugins.source.OnePlusBlePermissionHelper
import app.aaps.plugins.source.compose.DexcomOnePlusStartScreen
import app.aaps.plugins.source.compose.OnePlusBlePermissionGate
import dev.zacsweers.metro.Inject

/**
 * Android entry point of the sensor start screen.
 *
 * The screen itself is [DexcomOnePlusStartScreen]. What stays here is the Activity, the runtime
 * Bluetooth permission, and the two Intents that carry the user on to the warm-up and status
 * screens.
 */
class DexcomOnePlusStartActivity : MetroAppCompatActivity() {

    @Inject lateinit var dexcomOnePlusPlugin: DexcomOnePlusPlugin
    @Inject lateinit var dexcomOnePlusHost: DexcomOnePlusHost
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, pre-soak, switch). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        dexcomOnePlusPlugin.syncDriverFromPrefs()
        dexcomOnePlusHost.attachPlatform(OnePlusCgmDrivers.default())
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    DexcomOnePlusStartScreen(
                        host = dexcomOnePlusHost,
                        permissions = ActivityBlePermissionGate(this),
                        onBack = { finish() },
                        onEnsureDriver = {
                            dexcomOnePlusPlugin.syncDriverFromPrefs()
                            OnePlusCgmDrivers.default()
                        },
                        onActivatePlugin = {
                            configBuilder.performPluginSwitch(
                                changedPlugin = dexcomOnePlusPlugin,
                                enabled = true,
                                type = PluginType.BGSOURCE,
                            )
                        },
                        onStarted = { startedSlot ->
                            // PRODUCTION: hand the user to the warm-up screen so the new sensor is
                            // visibly taken over (pairing → warm-up countdown). Returning straight to
                            // the overview gave no confirmation that Connect had done anything.
                            // STAGING: the warm-up screen tracks the production driver only, so keep
                            // the collect-only flow returning to the overview.
                            // The BLE session keeps running on the driver daemon either way; we do
                            // NOT disconnect/shutdown here, only finish this Activity.
                            if (startedSlot == SensorSlot.PRODUCTION) {
                                startActivity(
                                    Intent(this, DexcomOnePlusWarmupActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                )
                            }
                            finish()
                        },
                        onBeginStaging = { address -> dexcomOnePlusPlugin.beginStaging(address) },
                        onIsStagingSensor = { address -> dexcomOnePlusPlugin.isStagingSensor(address) },
                        onCancelStaging = { dexcomOnePlusPlugin.cancelStaging() },
                        // Promotion has its own gates and its own confirmation, and they live on the
                        // status screen. Sending the user there is honest; re-implementing the gate
                        // here would be a second place to get a loop source switch wrong.
                        onOpenPromote = {
                            startActivity(
                                Intent(this, DexcomOnePlusStatusActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            )
                            finish()
                        },
                        // Straight from the driver registry, which is the same instance the
                        // plugin uses. The plugin hands out the OnePlusCgmDriver interface, while
                        // this screen needs the real driver's saveIdentity and its sighting-aware
                        // connect.
                        onStagingDriver = { OnePlusCgmDrivers.staging() },
                        onSensorSessionStarted = { address, previousMac ->
                            dexcomOnePlusPlugin.onSensorSessionStarted(address, previousMac)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The runtime Bluetooth permission of this phone, asked for from the Activity on top.
 *
 * With no Activity to ask from, the screen carries on as if the permission were there, and a scan
 * that hears nothing is what the user sees.
 */
private class ActivityBlePermissionGate(private val activity: Activity?) : OnePlusBlePermissionGate {

    override fun granted(): Boolean = activity == null || OnePlusBlePermissionHelper.hasAll(activity)

    override fun request() {
        activity?.let { OnePlusBlePermissionHelper.requestMissing(it) }
    }
}
