package app.aaps.plugins.source.activities

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.libre3.Libre3CgmDrivers
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.nfc.Libre3NfcFailure
import app.aaps.plugins.libre3.nfc.Libre3NfcReader
import app.aaps.plugins.libre3.nfc.Libre3NfcScanResult
import app.aaps.plugins.libre3.nfc.Libre3NfcSession
import app.aaps.plugins.source.Libre3BlePermissionHelper
import app.aaps.plugins.source.Libre3NativePlugin
import app.aaps.plugins.source.R
import app.aaps.plugins.source.compose.CgmCard
import app.aaps.plugins.source.compose.CgmCardHeader
import app.aaps.plugins.source.compose.CgmCardTone
import app.aaps.plugins.source.compose.CgmHelpCard
import app.aaps.plugins.source.compose.CgmLazyColumn
import app.aaps.plugins.source.compose.CgmNavIcon
import app.aaps.plugins.source.compose.CgmScaffold
import app.aaps.plugins.source.compose.CgmStateChip
import app.aaps.plugins.source.compose.CgmStepper
import app.aaps.plugins.source.compose.CgmUiState
import app.aaps.plugins.source.compose.CgmWidth
import app.aaps.plugins.source.compose.Libre3UiLabels
import app.aaps.plugins.source.compose.rememberCgmWindow
import dev.zacsweers.metro.Inject

/**
 * Starting a sensor.
 *
 * There is nothing to type. The user holds the phone on the sensor, the NFC step reads it, stores
 * it, and only then may Bluetooth start. The reader runs only while this screen is in front of the
 * user, so a phone in a pocket never scans.
 *
 * The screen never splits into two panes: pairing is a single NFC tap, so there is no list of
 * candidates and nothing to put in a second column. One column at every size; the screen scrolls,
 * the help folds away, and the current step is always named.
 *
 * The screen also asks **which slot** the scan is for, and that is the most safety relevant line of
 * this whole feature. See [slot].
 */
class Libre3StartActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences

    @Inject lateinit var plugin: Libre3NativePlugin

    private var reader: Libre3NfcReader? = null

    /**
     * Which slot the next scan writes into.
     *
     * ⚠️ This is what protects the sensor that feeds the loop. The store the NFC step writes is
     * chosen from this value, and `Libre3SensorStore.saveIdentityAndWait` drops the pairing key,
     * `k_enc` and `iv_enc` of the file it writes as soon as the serial changes. Writing a pre-soak
     * sensor into the production file would therefore throw the running sensor's keys away, and a
     * running Libre 3 refuses a fresh first pairing — the sensor on the arm would be lost for good.
     * The pre-soak slot must always land in its own file.
     *
     * Because of that it is written to the saved instance state by [onSaveInstanceState] and read
     * back in [onCreate], instead of living in memory only. Android rebuilds this activity for a
     * rotation, a dark mode flip, a font size change, split screen, and after killing the app in
     * the background. A slot that quietly fell back to production on any of those would send the
     * next scan into the production file, and the user would have no way of noticing: the picker
     * simply jumps back one button.
     */
    private var slot by mutableStateOf(SensorSlot.PRODUCTION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Other apps may not draw over this screen: an overlay could hide what a tap really does, here on a
        // sensor (start, calibration, pre-soak). Same as ComposeMainActivity. Needs HIDE_OVERLAY_WINDOWS.
        window.setHideOverlayWindows(true)
        slot = readSlot(savedInstanceState)
        // The reader is built once and never rebuilt on a slot change: it owns an executor and it
        // registers reader mode on this activity, and only one reader mode may be on at a time. The
        // store follows the slot through the supplier instead, so the toggle costs nothing. The
        // veto stops a scan of a sensor the other slot already holds.
        val nfcReader = Libre3NfcReader(
            Libre3NfcSession(
                { Libre3SensorStore(this, Libre3CgmDrivers.storeNamespace(slot)) },
                veto = ::sensorHeldByOtherSlot,
            ),
        )
        reader = nfcReader
        setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    Libre3StartScreen(
                        activity = this,
                        reader = nfcReader,
                        slot = slot,
                        onSlotChange = { slot = it },
                        onBack = { finish() },
                        onSensorScanned = ::onSensorScanned,
                    )
                }
            }
        }
    }

    /**
     * What to do with a sensor the NFC step has just stored, told apart by slot.
     *
     * @return the text that says why the sensor was not started, or null when it was.
     */
    @StringRes
    private fun onSensorScanned(result: Libre3NfcScanResult): Int? = when (slot) {
        SensorSlot.PRODUCTION -> {
            // Also while the plugin is stopped: the repeat guard must be ready for the new sensor.
            plugin.onSensorChanged()
            when {
                // The sensor is stored, and the next start of the plugin connects it.
                plugin.isStopped() -> R.string.libre3_plugin_stopped_sensor_saved
                else               -> {
                    if (result.readyForBle) {
                        plugin.connectStoredSensor(result.identity.bleAddress)
                    }
                    null
                }
            }
        }

        SensorSlot.STAGING    -> when {
            plugin.isStopped() -> R.string.libre3_plugin_stopped
            // Never onSensorChanged() here: it resets the process wide repeat guard, and the next
            // reading of the sensor that feeds the loop would then look new.
            plugin.beginStaging(result.identity) -> {
                if (result.readyForBle) {
                    plugin.connectStagingSensor(result.identity.bleAddress)
                }
                null
            }
            else                                 -> R.string.libre3_staging_is_production
        }
    }

    /**
     * Is this sensor already held by the slot the user is not starting?
     *
     * Asked by the NFC step with the serial read from the patch info, before the activation command
     * goes out.
     */
    private fun sensorHeldByOtherSlot(serial: String): Boolean = when (slot) {
        SensorSlot.PRODUCTION -> plugin.isStagingSensor(serial, mac = null)
        SensorSlot.STAGING    -> plugin.isProductionSensor(serial, mac = null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SLOT, slot.name)
    }

    /**
     * The slot written by [onSaveInstanceState], or production when there is nothing to read.
     *
     * A name that does not match any [SensorSlot] falls back to production.
     */
    private fun readSlot(saved: Bundle?): SensorSlot {
        val name = saved?.getString(KEY_SLOT) ?: return SensorSlot.PRODUCTION
        return runCatching { SensorSlot.valueOf(name) }.getOrDefault(SensorSlot.PRODUCTION)
    }

    override fun onDestroy() {
        super.onDestroy()
        reader?.shutdown()
        reader = null
    }

    companion object {

        /** Name of the chosen slot in the saved instance state. */
        private const val KEY_SLOT = "libre3_slot"
    }
}

@Composable
internal fun Libre3StartScreen(
    activity: Activity,
    reader: Libre3NfcReader,
    slot: SensorSlot,
    onSlotChange: (SensorSlot) -> Unit,
    onBack: () -> Unit,
    /** Returns the text that says why the sensor was not started, or null when it was. */
    onSensorScanned: (Libre3NfcScanResult) -> Int?,
    modifier: Modifier = Modifier,
) {
    val window = rememberCgmWindow()
    var scanned by remember { mutableStateOf<Libre3NfcScanResult?>(null) }
    var failure by remember { mutableStateOf<Libre3NfcFailure?>(null) }
    var scanRefusal by remember { mutableStateOf<Int?>(null) }
    var nfcAvailable by remember { mutableStateOf(true) }
    var permissionsGranted by remember { mutableStateOf(Libre3BlePermissionHelper.hasAll(activity)) }

    val changeSlot: (SensorSlot) -> Unit = { wanted ->
        scanned = null
        failure = null
        scanRefusal = null
        onSlotChange(wanted)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    nfcAvailable = reader.enable(
                        activity = activity,
                        onResult = { result ->
                            failure = null
                            scanned = result
                            scanRefusal = onSensorScanned(result)
                        },
                        onError = { reason -> failure = reason },
                    )
                }

                Lifecycle.Event.ON_PAUSE  -> reader.disable(activity)
                else                      -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            reader.disable(activity)
        }
    }

    val currentStep = when {
        !permissionsGranted -> 0
        !nfcAvailable       -> 1
        scanned == null     -> 2
        else                -> 3
    }

    CgmScaffold(
        title = stringResource(R.string.libre3_start_title),
        onNavigate = onBack,
        modifier = modifier,
        navIcon = CgmNavIcon.Close,
    ) {
        CgmLazyColumn {
            item(key = "stepper") {
                CgmStepper(
                    currentStep = currentStep,
                    labels = listOf(
                        stringResource(R.string.libre3_step_permissions),
                        stringResource(R.string.libre3_step_nfc),
                        stringResource(R.string.libre3_step_scan),
                        stringResource(
                            if (slot == SensorSlot.STAGING) R.string.libre3_step_presoak
                            else R.string.libre3_step_warmup
                        ),
                    ),
                    compact = window.width == CgmWidth.Compact,
                )
            }

            item(key = "slot") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = slot == SensorSlot.PRODUCTION,
                        onClick = { changeSlot(SensorSlot.PRODUCTION) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.libre3_slot_production_short))
                    }
                    SegmentedButton(
                        selected = slot == SensorSlot.STAGING,
                        onClick = { changeSlot(SensorSlot.STAGING) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.libre3_slot_staging_short))
                    }
                }
            }

            item(key = "state") {
                val result = scanned
                val problem = failure
                val refusal = scanRefusal
                when {
                    refusal != null      -> CgmCard(tone = CgmCardTone.Warning) {
                        CgmCardHeader(stringResource(R.string.libre3_scan_problem))
                        Text(
                            text = stringResource(refusal),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    result != null       -> CgmCard(accent = true) {
                        CgmCardHeader(stringResource(R.string.libre3_scan_ready)) {
                            CgmStateChip(
                                state = CgmUiState.Ready,
                                label = stringResource(R.string.libre3_phase_ready),
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.libre3_start_scanned,
                                result.identity.serialNumber,
                                Libre3UiLabels.generationLabel(result.identity.generation),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(
                                if (slot == SensorSlot.STAGING) R.string.libre3_presoak_done_note
                                else R.string.libre3_start_done_note
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    problem != null      -> CgmCard(tone = CgmCardTone.Warning) {
                        CgmCardHeader(stringResource(R.string.libre3_scan_problem))
                        Text(
                            text = Libre3UiLabels.nfcFailureLabel(problem),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.libre3_scan_retry_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    !permissionsGranted  -> CgmCard {
                        CgmCardHeader(stringResource(R.string.libre3_scan_heading))
                        Text(
                            text = stringResource(R.string.libre3_permissions_needed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                Libre3BlePermissionHelper.requestMissing(activity)
                                permissionsGranted = Libre3BlePermissionHelper.hasAll(activity)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.libre3_request_permissions))
                        }
                    }

                    !nfcAvailable        -> CgmCard(tone = CgmCardTone.Warning) {
                        CgmCardHeader(stringResource(R.string.libre3_scan_heading))
                        Text(
                            text = stringResource(R.string.libre3_start_nfc_off),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else                 -> CgmCard {
                        CgmCardHeader(stringResource(R.string.libre3_scan_heading)) {
                            CgmStateChip(
                                state = CgmUiState.Working,
                                label = stringResource(R.string.libre3_scan_waiting_chip),
                            )
                        }
                        Text(
                            text = stringResource(R.string.libre3_start_scan_hint),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.libre3_start_scan_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "help") {
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                    if (slot == SensorSlot.STAGING) {
                        CgmHelpCard(
                            title = stringResource(R.string.libre3_presoak_wear_cost_title),
                            tone = CgmCardTone.Warning,
                            expandedByDefault = true,
                        ) {
                            Text(
                                text = stringResource(R.string.libre3_presoak_wear_cost),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    CgmHelpCard(title = stringResource(R.string.libre3_help_how_to_start)) {
                        Text(
                            text = stringResource(R.string.libre3_start_steps),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    CgmHelpCard(
                        title = stringResource(R.string.libre3_help_before_scanning),
                        tone = CgmCardTone.Warning,
                    ) {
                        Text(
                            text = stringResource(R.string.libre3_start_conflict_warning),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Libre3StartScreenPreview() {
    MaterialTheme {
        Text(stringResource(R.string.libre3_start_scan_hint))
    }
}
