package app.aaps.plugins.source.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverReal
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.identity.OnePlusAdvCandidate
import app.aaps.plugins.dexcomoneplus.identity.OnePlusGs1ApplicatorParser
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanResult
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanSelection
import app.aaps.plugins.dexcomoneplus.session.OnePlusSessionStart
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.SourceStrings
import kotlinx.coroutines.launch

/**
 * Sensor start: BLE permissions, LE scan (DX02/FEBC), applicator GS1 / PIN, connect → warm-up.
 *
 * Everything the phone itself owns - the key-value file of a slot, the LE scanner, the Bluetooth
 * permission - arrives through [DexcomOnePlusHost] and [permissions], so the screen can be tested
 * without an Activity.
 *
 * @param host the phone this screen runs on.
 * @param permissions the Bluetooth permission gate of that phone.
 * @param onStagingDriver the pre-soak driver, or **null** when there is no second sensor slot.
 *   Null is what makes the slot chooser absent rather than present and broken.
 */
@Composable
fun DexcomOnePlusStartScreen(
    host: DexcomOnePlusHost,
    permissions: OnePlusBlePermissionGate,
    onBack: () -> Unit,
    onEnsureDriver: () -> OnePlusCgmDriver,
    onActivatePlugin: () -> Unit,
    onStarted: (SensorSlot) -> Unit,
    /** STAGING only: the user started this sensor now — anchors the pre-soak clock on its MAC. */
    onBeginStaging: (String) -> Boolean,
    /** True when that address is the sensor the pre-soak slot holds. */
    onIsStagingSensor: (String) -> Boolean,
    onCancelStaging: () -> Unit,
    onOpenPromote: () -> Unit,
    onStagingDriver: (() -> OnePlusCgmDriverReal)?,
    /**
     * PRODUCTION only: the user started this sensor now → anchor its age and log a sensor change.
     * Called with (MAC of the started sensor, MAC stored before this start).
     */
    onSensorSessionStarted: (String, String?) -> Unit,
) {
    val window = rememberCgmWindow()
    val scope = rememberCoroutineScope()
    // Which slot the guided flow starts. PRODUCTION keeps the existing behaviour (activates the
    // plugin + feeds the loop after warm-up). STAGING pre-soaks a second sensor collect-only — it
    // never touches the AAPS active BG source until promoted from the status screen.
    var slot by remember { mutableStateOf(SensorSlot.PRODUCTION) }
    // EVERY store read below follows the selected slot. Reading the production store while starting a
    // STAGING sensor pre-filled the old sensor's code and MAC and ranked the scan with the old
    // sensor's fingerprint, so a pre-soak silently re-adopted the sensor already in use (seen in a
    // field log: both slots connecting to the same MAC).
    val sensorStore = remember(slot) { host.slotStore(OnePlusCgmDrivers.storeNamespace(slot)) }
    val storedSession = remember(slot) { sensorStore.load() }
    val scanner = remember { host.screenScanner() }
    val focusManager = LocalFocusManager.current

    var applicatorInput by remember { mutableStateOf("") }
    var parsedIdentity by remember { mutableStateOf<OnePlusSensorIdentity?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<OnePlusScanResult?>(null) }
    /** The user tapped a sensor in the scan list — auto-select must not overrule that choice. */
    var deviceChosenByUser by remember { mutableStateOf(false) }
    val devices = remember { mutableStateListOf<OnePlusScanResult>() }
    // The applicator field is pre-filled with the *stored* sensor's code. Picking a different MAC
    // while leaving that code untouched pairs a NEW transmitter with the OLD sensor's PIN: auth
    // fails and the slot keeps a session start that is not this sensor's. Warn once, then let the
    // user proceed (a re-typed identical code is legitimate, if unlikely).
    var newSensorConfirmPending by remember { mutableStateOf(false) }
    /** Set when Connect targets the sensor the pre-soak slot is already running — see the dialog. */
    var slotConflictAddress by remember { mutableStateOf<String?>(null) }

    // Re-seed the whole form from the slot's own store, on first composition and on every slot
    // switch, so what is shown always belongs to the sensor being started.
    //
    // The selection is not seeded here: it follows the code on screen, in the effect below.
    LaunchedEffect(slot) {
        applicatorInput = storedSession?.identity?.rawGs1 ?: storedSession?.identity?.pin.orEmpty()
        parsedIdentity = storedSession?.identity ?: OnePlusGs1ApplicatorParser.parse(applicatorInput)
        selected = null
        deviceChosenByUser = false
        devices.clear()
        errorText = null
        newSensorConfirmPending = false
    }

    // Rank this scan from the identity ON SCREEN, not from the store. As soon as the code belongs to
    // another sensor, the stored MAC and ADV name lose their boost — see OnePlusAdvCandidate.
    val scanHint = remember(storedSession, parsedIdentity) {
        OnePlusAdvCandidate.scanHintFor(storedSession, parsedIdentity)
    }
    // What the scan callback below has to read: the hint as it is NOW, not the one captured when the
    // callback was built. The user may type another code while the scan runs.
    val liveScanHint: State<OnePlusStoredSession?> = rememberUpdatedState(scanHint)
    // The selection follows the code: the stored sensor stays pre-selected while its own code is on
    // screen (a reconnect needs no choosing), and the moment the code becomes another sensor's the
    // pre-selection goes, because from there on only the user knows which transmitter is theirs.
    LaunchedEffect(scanHint) {
        scanner.updateSessionHint(scanHint)
        if (!deviceChosenByUser) {
            selected = OnePlusScanSelection.autoSelect(devices, scanHint)
        }
    }

    val codeRequired = stringResource(SourceStrings.dexcom_oneplus_pairing_code_required)
    val codeInvalid = stringResource(SourceStrings.dexcom_oneplus_pairing_code_invalid)
    val permissionsNeeded = stringResource(SourceStrings.dexcom_oneplus_permissions_needed)
    val deviceRequired = stringResource(SourceStrings.dexcom_oneplus_device_required)
    val serialNone = stringResource(SourceStrings.dexcom_oneplus_applicator_serial_none)
    val newSensorCodeWarning = stringResource(SourceStrings.dexcom_oneplus_new_sensor_code_warning)
    val stagingIsProduction = stringResource(SourceStrings.dexcom_oneplus_staging_is_production)
    var connectRequested by remember { mutableStateOf(false) }

    // Guided-flow progress for the stepper: Prepare → Code → Connect → Warm-up.
    val hasPermissions = permissions.granted()
    val hasDevice = selected != null
    val currentStep = DexcomOnePlusStartRules.currentStep(
        hasPermissions = hasPermissions,
        hasCode = parsedIdentity != null,
        hasDevice = hasDevice,
    )

    DisposableEffect(Unit) {
        onDispose {
            // Do not stopScan after Connect — this screen's dispose races Warmup navigation and
            // was killing the driver's pre-connect LE scan on the shared scanner (ADV miss).
            if (!connectRequested) {
                scanner.stopScan()
            }
        }
    }

    // Connect is the step that actually adopts the selected sensor (writes its MAC/PIN into the slot
    // store and starts the session). It is pinned in the Scaffold bottom bar: as a last child of a
    // non-scrolling Column it was pushed off-screen as soon as the scan list filled up, leaving the
    // user with a selected device and no way to validate it — and the slot store still holding the
    // PREVIOUS sensor's MAC.
    val onConnectClick: () -> Unit = onConnectClick@{
        val identity = parsedIdentity
            ?: OnePlusGs1ApplicatorParser.parse(applicatorInput)
        if (identity == null) {
            errorText = codeRequired
            return@onConnectClick
        }
        val code = OnePlusSessionStart.normalizePairingCode(identity.pin)
        if (!OnePlusSessionStart.isValidPairingCode(code)) {
            errorText = codeInvalid
            return@onConnectClick
        }
        if (!permissions.granted()) {
            errorText = permissionsNeeded
            permissions.request()
            return@onConnectClick
        }
        val address = selected?.address
        if (address.isNullOrBlank()) {
            errorText = deviceRequired
            return@onConnectClick
        }
        scanner.stopScan()
        scanning = false
        connectRequested = true
        // Hand off the freshest live sighting (carries seenElapsedMs) so the driver
        // connects in-window instead of blindly re-scanning.
        //
        // Only a sighting that was really heard is handed over. The pre-selection may be a stored
        // entry the scan never confirmed; passing that pretends an advertisement arrived, which both
        // misses the driver's fast path anyway and talks it out of requiring an ADV. null is the
        // honest answer, and the driver then scans for the sensor itself.
        val sighting = devices.firstOrNull { it.address == address }
            ?: selected?.takeIf { it.seenElapsedMs > 0L }
        if (slot == SensorSlot.STAGING) {
            // Staging: collect-only. Never write the production store, never activate the
            // plugin, never switch the AAPS active BG source — just drive scan/connect on
            // the dedicated staging driver and return to the overview.
            //
            // Soaking the sensor that already feeds the loop would put two KEKS handshakes on one
            // link and break both. The plugin refuses it too; this is the message the user reads.
            if (!onBeginStaging(address)) {
                errorText = stagingIsProduction
                return@onConnectClick
            }
            // Not null here: the STAGING slot can only be chosen when the chooser is drawn, and the
            // chooser is drawn only when this phone has a pre-soak driver.
            val stagingDriverFactory = checkNotNull(onStagingDriver) { "STAGING chosen on a one slot phone" }
            val stagingDriver = stagingDriverFactory()
            host.attachPlatform(stagingDriver)
            stagingDriver.saveIdentity(identity.copy(pin = code))
            stagingDriver.connect(
                deviceAddress = address,
                pairingCode = code,
                sighting = sighting,
            )
            onStarted(SensorSlot.STAGING)
            return@onConnectClick
        }
        // Production start on the sensor that is soaking in the other slot: the same collision, from
        // the other side. Ask rather than act — cancelling the pre-soak silently would throw away a
        // soak clock that is often many hours old, and promoting is usually what the user wants.
        if (onIsStagingSensor(address)) {
            slotConflictAddress = address
            return@onConnectClick
        }
        // Production: adopting a different transmitter with the pre-filled code of the previous one
        // cannot work (the PIN is per-sensor). Ask for confirmation once before overwriting the slot.
        val storedMac = storedSession?.lastMac
        val adoptingAnotherSensor = DexcomOnePlusStartRules.adoptsAnotherSensor(storedMac, address)
        // The safe call is only for the compiler: a non-null storedMac means a non-null session,
        // and adoptingAnotherSensor is false without one.
        if (adoptingAnotherSensor && storedSession?.identity?.pin == code && !newSensorConfirmPending) {
            newSensorConfirmPending = true
            errorText = newSensorCodeWarning
            return@onConnectClick
        }
        sensorStore.saveIdentity(identity.copy(pin = code))
        sensorStore.saveLastMac(address)
        selected?.name?.let { sensorStore.saveLastDeviceName(it) }
        // Anchor the sensor age on this explicit start (overview sensor age / SENSOR_CHANGE). After
        // saveIdentity, which drops the stored start when the serial shows another sensor. storedMac
        // is the MAC read at screen entry, so it still describes the sensor that was running.
        onSensorSessionStarted(address, storedMac)
        val activeDriver = onEnsureDriver()
        host.attachPlatform(activeDriver)
        (activeDriver as? OnePlusCgmDriverReal)?.saveIdentity(identity.copy(pin = code))
        if (activeDriver is OnePlusCgmDriverReal) {
            activeDriver.connect(
                deviceAddress = address,
                pairingCode = code,
                sighting = sighting,
            )
        } else {
            activeDriver.connect(deviceAddress = address, pairingCode = code)
        }
        onActivatePlugin()
        onStarted(SensorSlot.PRODUCTION)
    }

    val onStartScan: () -> Unit = onStartScan@{
        if (!permissions.granted()) {
            errorText = permissionsNeeded
            permissions.request()
            return@onStartScan
        }
        devices.clear()
        deviceChosenByUser = false
        scanner.updateSessionHint(scanHint)
        selected = OnePlusScanSelection.autoSelect(devices, scanHint)
        scanner.startScan { hit ->
            // The scanner answers on its own thread (a binder thread). This scope belongs to the
            // composition, so the list below is only ever
            // touched on the UI thread, and a hit that arrives after the screen is gone is dropped
            // rather than written into a composition that no longer exists.
            scope.launch {
                val idx = devices.indexOfFirst { it.address == hit.address }
                if (idx >= 0) devices[idx] = hit else devices.add(hit)
                // The live hint, not the one captured when this lambda was built: the user may type
                // another code while the scan runs. One sensor in range may be selected for them,
                // two or more may not. This is an assignment on purpose — when a second sensor turns
                // up, the single-hit suggestion has to go, and Connect goes with it.
                if (!deviceChosenByUser) {
                    selected = OnePlusScanSelection.autoSelect(devices, liveScanHint.value)
                }
            }
        }
        scanning = true
        errorText = null
    }

    val onStopScan: () -> Unit = {
        scanner.stopScan()
        scanning = false
    }

    val onDeviceClick: (OnePlusScanResult) -> Unit = { device ->
        selected = device
        deviceChosenByUser = true
        errorText = null
        newSensorConfirmPending = false
    }

    val rankedDevices = DexcomOnePlusStartRules.rank(devices, scanHint)
    val connectAction: @Composable ColumnScope.() -> Unit = {
        ConnectAction(
            slot = slot,
            errorText = errorText,
            selectedLabel = selected?.name ?: selected?.address,
            enabled = hasDevice,
            onConnectClick = onConnectClick,
        )
    }

    CgmScaffold(
        title = stringResource(SourceStrings.dexcom_oneplus_start_title),
        onNavigate = onBack,
        // A screen the user either commits (Connect) or abandons — see the AapsTopAppBar convention.
        navIcon = CgmNavIcon.Close,
        constrainWidth = !window.isTwoPane,
        bottomBar = {
            // In two panes the action lives at the foot of the form column, next to what it acts on.
            if (!window.isTwoPane) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AapsSpacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    content = connectAction,
                )
            }
        },
    ) {
        if (window.isTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clearFocusOnTap(focusManager),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge),
            ) {
                Column(
                    // Both panes share the width. A fixed 560 dp form would have left the sensor
                    // list about 250 dp at the 840 dp split point — narrower than one row needs.
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = CgmSpacing.readableContentMaxWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(AapsSpacing.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                ) {
                    FormPane(
                        window = window,
                        currentStep = currentStep,
                        slot = slot,
                        onSlotChange = { slot = it; errorText = null },
                        slotChoiceOffered = onStagingDriver != null,
                        permissions = permissions,
                        applicatorInput = applicatorInput,
                        parsedIdentity = parsedIdentity,
                        errorText = errorText,
                        serialNone = serialNone,
                        storedMac = storedSession?.lastMac,
                        onApplicatorChange = { text ->
                            applicatorInput = text
                            parsedIdentity = OnePlusGs1ApplicatorParser.parse(text)
                            errorText = null
                            newSensorConfirmPending = false
                        },
                    )
                    connectAction()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            top = AapsSpacing.extraLarge,
                            end = AapsSpacing.extraLarge,
                            bottom = AapsSpacing.extraLarge,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                ) {
                    ScanHeader(
                        scanning = scanning,
                        deviceCount = devices.size,
                        onStartScan = onStartScan,
                        onStopScan = onStopScan,
                    )
                    // Its own pane, so the list gets the whole height instead of a fixed 216 dp box.
                    CgmLazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        deviceItems(
                            devices = rankedDevices,
                            selectedAddress = selected?.address,
                            storedMac = storedSession?.lastMac,
                            onDeviceClick = onDeviceClick,
                        )
                    }
                }
            }
        } else {
            CgmLazyColumn(
                modifier = Modifier.clearFocusOnTap(focusManager),
            ) {
                item(key = "form") {
                    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                        FormPane(
                            window = window,
                            currentStep = currentStep,
                            slot = slot,
                            onSlotChange = { slot = it; errorText = null },
                            slotChoiceOffered = onStagingDriver != null,
                            permissions = permissions,
                            applicatorInput = applicatorInput,
                            parsedIdentity = parsedIdentity,
                            errorText = errorText,
                            serialNone = serialNone,
                            storedMac = storedSession?.lastMac,
                            onApplicatorChange = { text ->
                                applicatorInput = text
                                parsedIdentity = OnePlusGs1ApplicatorParser.parse(text)
                                errorText = null
                                newSensorConfirmPending = false
                            },
                        )
                    }
                }
                item(key = "scanHeader") {
                    ScanHeader(
                        scanning = scanning,
                        deviceCount = devices.size,
                        onStartScan = onStartScan,
                        onStopScan = onStopScan,
                    )
                }
                // The scanned sensors are items of the screen's own list. Wrapping them in a list
                // with its own bounded height was what pinned them to 216 dp on every screen size.
                deviceItems(
                    devices = rankedDevices,
                    selectedAddress = selected?.address,
                    storedMac = storedSession?.lastMac,
                    onDeviceClick = onDeviceClick,
                )
                item(key = "help") {
                    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                        HelpSection(slot = slot, slotChoiceOffered = onStagingDriver != null)
                    }
                }
            }
        }
    }

    slotConflictAddress?.let {
        SlotConflictDialog(
            onPromote = {
                slotConflictAddress = null
                onOpenPromote()
            },
            onCancelStagingAndStart = {
                slotConflictAddress = null
                // The soak is given up knowingly. Connect is not re-run for the user: the store has
                // just changed under it, so they press it again on a screen that now tells the truth.
                onCancelStaging()
                errorText = null
            },
            onDismiss = { slotConflictAddress = null },
        )
    }
}

/**
 * Asked when Connect targets the sensor that is soaking in the pre-soak slot.
 *
 * Promote is the primary action on purpose: it is what the user usually means, and it keeps the soak
 * the sensor has already earned. Cancelling throws that away, so it is the quieter choice, and it
 * says so.
 */
@Composable
private fun SlotConflictDialog(
    onPromote: () -> Unit,
    onCancelStagingAndStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(SourceStrings.dexcom_oneplus_slot_conflict_title)) },
        text = { Text(stringResource(SourceStrings.dexcom_oneplus_slot_conflict_message)) },
        confirmButton = {
            TextButton(onClick = onPromote) {
                Text(stringResource(SourceStrings.dexcom_oneplus_slot_conflict_promote))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(SourceStrings.dexcom_oneplus_slot_conflict_dismiss))
                }
                TextButton(onClick = onCancelStagingAndStart) {
                    Text(stringResource(SourceStrings.dexcom_oneplus_slot_conflict_restart))
                }
            }
        },
    )
}

/** The stepper, slot choice, permission prompt, code field and help — everything but the scan. */
@Composable
private fun ColumnScope.FormPane(
    window: CgmWindow,
    currentStep: Int,
    slot: SensorSlot,
    onSlotChange: (SensorSlot) -> Unit,
    slotChoiceOffered: Boolean,
    permissions: OnePlusBlePermissionGate,
    applicatorInput: String,
    parsedIdentity: OnePlusSensorIdentity?,
    errorText: String?,
    serialNone: String,
    storedMac: String?,
    onApplicatorChange: (String) -> Unit,
) {
    CgmStepper(
        currentStep = currentStep,
        labels = listOf(
            stringResource(SourceStrings.dexcom_oneplus_step_prepare),
            stringResource(SourceStrings.dexcom_oneplus_step_code),
            stringResource(SourceStrings.dexcom_oneplus_step_connect),
            stringResource(SourceStrings.dexcom_oneplus_step_warmup),
        ),
        // Four labels never fit across a phone; the compact form shows one at a time instead of
        // truncating all of them.
        compact = window.width == CgmWidth.Compact,
    )

    // A real single-choice control: the selected side carries the selection state, so TalkBack
    // announces a choice instead of two buttons, one of which used to do nothing at all.
    //
    // Left out entirely on a phone with one sensor slot. A disabled second button would read as
    // "pre-soak is off just now"; absent reads as "this phone does not do that", which is the truth.
    if (slotChoiceOffered) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = slot == SensorSlot.PRODUCTION,
                onClick = { onSlotChange(SensorSlot.PRODUCTION) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(SourceStrings.dexcom_oneplus_slot_production_short))
            }
            SegmentedButton(
                selected = slot == SensorSlot.STAGING,
                onClick = { onSlotChange(SensorSlot.STAGING) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(SourceStrings.dexcom_oneplus_slot_staging_short))
            }
        }
    }

    if (!permissions.granted()) {
        Button(
            onClick = { permissions.request() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(SourceStrings.dexcom_oneplus_request_permissions))
        }
    }

    CgmCard {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_section_pairing_code))
        OutlinedTextField(
            value = applicatorInput,
            onValueChange = onApplicatorChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(SourceStrings.dexcom_oneplus_applicator_gs1_label)) },
            supportingText = {
                if (parsedIdentity != null) {
                    Text(
                        stringResource(
                            SourceStrings.dexcom_oneplus_applicator_parsed,
                            parsedIdentity.pin,
                            parsedIdentity.serial ?: serialNone,
                        ),
                    )
                } else {
                    Text(stringResource(SourceStrings.dexcom_oneplus_applicator_gs1_hint))
                }
            },
            singleLine = false,
            minLines = 2,
            isError = errorText != null && parsedIdentity == null,
        )
        storedMac?.let { mac ->
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_last_mac_hint, mac),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (window.isTwoPane) HelpSection(slot = slot, slotChoiceOffered = slotChoiceOffered)
}

/**
 * The five help blocks that used to sit permanently open above the first control — about two
 * screens of scrolling before the user could reach Scan. Folded away, but the one that warns about
 * losing the Dexcom app keeps its warning colour while closed.
 */
@Composable
private fun ColumnScope.HelpSection(slot: SensorSlot, slotChoiceOffered: Boolean) {
    CgmHelpCard(title = stringResource(SourceStrings.dexcom_oneplus_help_how_to_start)) {
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_start_steps),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    CgmHelpCard(
        title = stringResource(SourceStrings.dexcom_oneplus_help_before_pairing),
        tone = CgmCardTone.Warning,
    ) {
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_dexcom_recovery_warning),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    CgmHelpCard(title = stringResource(SourceStrings.dexcom_oneplus_help_permissions)) {
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_permissions_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_scan_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    // Nothing to explain about slots on a phone that has only one.
    if (slotChoiceOffered) {
        CgmHelpCard(title = stringResource(SourceStrings.dexcom_oneplus_help_slot)) {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_slot_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScanHeader(
    scanning: Boolean,
    deviceCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_section_sensors_nearby)) {
            CgmStateChip(
                state = if (scanning) CgmUiState.Working else CgmUiState.Waiting,
                label = if (scanning) {
                    stringResource(SourceStrings.dexcom_oneplus_scan_scanning)
                } else {
                    stringResource(SourceStrings.dexcom_oneplus_scan_idle, deviceCount)
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        ) {
            OutlinedButton(
                onClick = onStartScan,
                modifier = Modifier.weight(1f),
                enabled = !scanning,
            ) {
                Text(
                    stringResource(
                        if (deviceCount > 0) {
                            SourceStrings.dexcom_oneplus_scan_rescan
                        } else {
                            SourceStrings.dexcom_oneplus_scan_start
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = onStopScan,
                modifier = Modifier.weight(1f),
                enabled = scanning,
            ) {
                Text(stringResource(SourceStrings.dexcom_oneplus_scan_stop))
            }
        }
        // Why every nearby sensor is listed, and why the app cannot choose for the user.
        Text(
            text = stringResource(SourceStrings.dexcom_oneplus_scan_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Scanned sensors as items of the caller's list — never a bounded list inside another scroll. */
private fun LazyListScope.deviceItems(
    devices: List<OnePlusScanResult>,
    selectedAddress: String?,
    storedMac: String?,
    onDeviceClick: (OnePlusScanResult) -> Unit,
) {
    if (devices.isEmpty()) {
        item(key = "scanEmpty") {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_scan_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(devices, key = { it.address }) { device ->
            DeviceRow(
                device = device,
                selected = selectedAddress == device.address,
                lastUsed = storedMac?.equals(device.address, ignoreCase = true) == true,
                onClick = { onDeviceClick(device) },
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: OnePlusScanResult,
    selected: Boolean,
    lastUsed: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AapsSpacing.large)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CgmSpacing.listRowMinHeight)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: stringResource(SourceStrings.dexcom_oneplus_scan_unnamed),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastUsed) {
                Text(
                    text = stringResource(SourceStrings.dexcom_oneplus_scan_last_used),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        SignalBars(rssi = device.rssi)
    }
}

/** Signal strength as bars - see [DexcomOnePlusStartRules.signalBars] for why bars and not dBm. */
@Composable
private fun SignalBars(rssi: Int) {
    val bars = DexcomOnePlusStartRules.signalBars(rssi)
    // The bars are decorative; the dBm value they stand for is what gets read out, so nothing is
    // lost for screen reader users.
    val description = stringResource(SourceStrings.dexcom_oneplus_scan_signal, rssi)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall),
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..4).forEach { index ->
            Box(
                modifier = Modifier
                    .width(AapsSpacing.small)
                    .height(AapsSpacing.small * (index + 1))
                    .background(
                        color = if (index <= bars) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(AapsSpacing.extraSmall),
                    ),
            )
        }
    }
}

@Composable
private fun ColumnScope.ConnectAction(
    slot: SensorSlot,
    errorText: String?,
    selectedLabel: String?,
    enabled: Boolean,
    onConnectClick: () -> Unit,
) {
    errorText?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    // Connect overwrites the slot's stored MAC, so the screen says which sensor it is about to
    // adopt rather than leaving the user to check the highlighted row.
    Text(
        text = when {
            slot == SensorSlot.STAGING -> stringResource(SourceStrings.dexcom_oneplus_staging_return_note)
            selectedLabel != null      -> stringResource(SourceStrings.dexcom_oneplus_connect_target, selectedLabel)
            else                       -> stringResource(SourceStrings.dexcom_oneplus_start_return_warmup)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onConnectClick,
        modifier = Modifier.fillMaxWidth(),
        // Selecting a device is what the button acts on: disabled until there is one, so
        // "nothing happens" is never mistaken for a broken button.
        enabled = enabled,
    ) {
        Text(
            if (slot == SensorSlot.STAGING) {
                stringResource(SourceStrings.dexcom_oneplus_staging_connect)
            } else {
                stringResource(SourceStrings.dexcom_oneplus_connect_follow)
            },
        )
    }
}
