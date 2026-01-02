package info.nightscout.pump.combov2.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import info.nightscout.comboctl.base.BasicProgressStage
import info.nightscout.comboctl.base.PAIRING_PIN_SIZE
import info.nightscout.comboctl.base.PairingPIN
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R
import info.nightscout.pump.combov2.databinding.Combov2PairingActivityBinding
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

// A counterpart to BlePreCheckImpl that is designed for coroutines.
private class BluetoothPermissionChecks(
    private val activity: ComponentActivity,
    private val permissions: List<String>,
    private val aapsLogger: AAPSLogger
) {

    private val activityResultLauncher: ActivityResultLauncher<Array<String>>
    private var waitForCompletion: CompletableJob? = null

    init {
        activityResultLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            waitForCompletion?.complete()
        }
    }

    suspend fun requestAndCheck() {
        val missingPermissions = permissions
            .filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()

        if (missingPermissions.isEmpty())
            return

        aapsLogger.debug(LTag.PUMP, "Missing permissions: " + missingPermissions.joinToString(", "))

        waitForCompletion = Job()
        activityResultLauncher.launch(missingPermissions)
        waitForCompletion?.join()
        waitForCompletion = null
    }

    fun unregister() {
        activityResultLauncher.unregister()
    }
}

class ComboV2PairingActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var combov2Plugin: ComboV2Plugin

    private var uiInitialized = false
    private var unregisterActivityLauncher = {}
    private var bluetoothPermissionChecks: BluetoothPermissionChecks? = null
    private lateinit var binding: Combov2PairingActivityBinding
    private var pinTextWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install an activity result caller for when the user presses
        // "deny" or "reject" in the dialog that pops up when Android
        // asks for permission to enable device discovery. In such a
        // case, without this caller, the logic would continue to look
        // for devices even though discovery isn't actually happening.
        // With this caller, we can cancel pairing in this case instead.
        val startPairingActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                RESULT_CANCELED -> {
                    aapsLogger.info(LTag.PUMP, "User rejected discovery request; cancelling pairing")
                    combov2Plugin.cancelPairing()
                }

                else            -> Unit
            }
        }
        combov2Plugin.customDiscoveryActivityStartCallback = { intent ->
            startPairingActivityLauncher.launch(intent)
        }
        unregisterActivityLauncher = {
            startPairingActivityLauncher.unregister()
        }

        binding = DataBindingUtil.setContentView(
            this, R.layout.combov2_pairing_activity
        )

        title = rh.gs(R.string.combov2_pair_with_pump_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val thisActivity = this

        // Set the pairing sections to initially show the "not initialized" one
        // in case the Bluetooth permissions haven't been granted yet by the user.
        binding.combov2PairingSectionInitial.visibility = View.GONE
        binding.combov2PairingSectionCannotPairDriverNotInitialized.visibility = View.VISIBLE

        // Launch the BluetoothPermissionChecks in the CREATED lifecycle state.
        // This is important, because registering an activity (which the
        // BluetoothPermissionChecks class does) must take place _before_ the
        // STARTED state is reached.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                aapsLogger.debug(LTag.PUMP, "Creating and registering BT permissions check object")
                bluetoothPermissionChecks = BluetoothPermissionChecks(
                    thisActivity,
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    aapsLogger
                )
            }

            // Unregister any activity that BluetoothPermissionChecks previously
            // registered if this pairing activity is getting destroyed.
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.DESTROYED) {
                    aapsLogger.debug(LTag.PUMP, "Unregistering BT permissions check object")
                    bluetoothPermissionChecks?.unregister()
                    bluetoothPermissionChecks = null
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                bluetoothPermissionChecks?.let {
                    aapsLogger.debug(LTag.PUMP, "Requesting and checking BT permissions")
                    it.requestAndCheck()
                }
                combov2Plugin.driverStateUIFlow
                    .onEach { driverState ->
                        if (!uiInitialized) {
                            when (driverState) {
                                // In the NotInitialized state, the PumpManager is unavailable because it cannot
                                // function without Bluetooth permissions. Several of ComboV2Plugin's functions
                                // such as getPairingProgressFlow() depend on PumpManager though. To prevent UI
                                // controls from becoming active without having a PumpManager, show instead a
                                // view on the activity that explains why pairing is currently not possible.
                                ComboV2Plugin.DriverState.NotInitialized -> {
                                    aapsLogger.info(LTag.PUMP, "Cannot pair right now; disabling pairing UI controls, showing message instead")

                                    binding.combov2PairingSectionInitial.visibility = View.GONE
                                    binding.combov2PairingSectionCannotPairDriverNotInitialized.visibility = View.VISIBLE

                                    binding.combov2CannotPairGoBack.setOnClickListener {
                                        finish()
                                    }
                                }

                                else                                     -> {
                                    binding.combov2PairingSectionCannotPairDriverNotInitialized.visibility = View.GONE
                                    setupUi(binding)
                                    uiInitialized = true
                                }
                            }
                        }
                    }
                    .launchIn(this)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                aapsLogger.info(LTag.PUMP, "User pressed the back button; cancelling any ongoing pairing")
                combov2Plugin.cancelPairing()
                finish()
            }
        })
    }

    override fun onDestroy() {
        // Clear all listeners to prevent memory leaks
        if (::binding.isInitialized) {
            binding.combov2CannotPairGoBack.setOnClickListener(null)
            binding.combov2PairingFinishedOk.setOnClickListener(null)
            binding.combov2PairingAborted.setOnClickListener(null)
            pinTextWatcher?.let { binding.combov2PinEntryEdit.removeTextChangedListener(it) }
            binding.combov2EnterPin.setOnClickListener(null)
            binding.combov2StartPairing.setOnClickListener(null)
            binding.combov2CancelPairing.setOnClickListener(null)
        }

        // In the NotInitialized state, getPairingProgressFlow() crashes because there
        // is no PumpManager present. But in that state, the pairing progress flow needs
        // no reset because no pairing can happen in that state anyway.
        if (combov2Plugin.driverStateUIFlow.value != ComboV2Plugin.DriverState.NotInitialized) {
            // Reset the pairing progress reported to allow for future pairing attempts.
            // Do this only after pairing was finished or aborted. onDestroy() can be
            // called in the middle of a pairing process, and we do not want to reset
            // the progress reporter mid-pairing.
            when (combov2Plugin.getPairingProgressFlow().value.stage) {
                BasicProgressStage.Finished,
                is BasicProgressStage.Aborted -> {
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Resetting pairing progress reporter after pairing was finished/aborted"
                    )
                    combov2Plugin.resetPairingProgress()
                }

                else                          -> Unit
            }
        }

        // Remove the activity start callback and unregister the activity
        // launcher to make sure that future registerForActivityResult()
        // calls start from a blank slate. (This is about the discovery
        // activity, not about the BluetoothPermissionChecks ones.)
        combov2Plugin.customDiscoveryActivityStartCallback = null
        unregisterActivityLauncher.invoke()
        unregisterActivityLauncher = {}

        super.onDestroy()
    }

    private fun setupUi(binding: Combov2PairingActivityBinding) {
        binding.combov2PairingFinishedOk.setOnClickListener {
            finish()
        }
        binding.combov2PairingAborted.setOnClickListener {
            finish()
        }

        val pinFormatRegex = "(\\d{1,3})(\\d{1,3})?(\\d{1,4})?".toRegex()
        val nonDigitsRemovalRegex = "\\D".toRegex()
        val whitespaceRemovalRegex = "\\s".toRegex()

        // Add a custom TextWatcher to format the PIN in the
        // same format it is shown on the Combo LCD, which is:
        //
        //     xxx xxx xxxx
        pinTextWatcher = object : TextWatcher {
            var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Store the text as it is before the change. We need this
                // to later determine if digits got added or removed.
                previousText = binding.combov2PinEntryEdit.text.toString()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Nothing needs to be done here; overridden method only exists
                // to properly and fully implement the TextWatcher interface.
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null)
                    return

                val originalText = s.toString()
                val trimmedText = originalText.trim().replace(nonDigitsRemovalRegex, "")

                val digitGroupValues = pinFormatRegex.matchEntire(trimmedText)?.let { matchResult ->
                    // Get the digit groups. Skip the first group, which contains the entire original string.
                    if (matchResult.groupValues.isEmpty())
                        listOf()
                    else
                        matchResult.groupValues.subList(1, matchResult.groupValues.size)
                } ?: listOf()

                // Join the groups to a string with a whitespace in between to construct
                // a correct PIN string (see the format above). Skip empty groups to
                // not have a trailing whitespace.
                val processedText = digitGroupValues.filter { it.isNotEmpty() }.joinToString(" ")

                if (originalText != processedText) {
                    // Remove and add this listener to modify the text
                    // without causing an infinite loop (text is changed,
                    // listener is called, listener changes text).
                    binding.combov2PinEntryEdit.removeTextChangedListener(this)

                    val trimmedPreviousText = previousText.trim().replace(nonDigitsRemovalRegex, "")

                    // Shift the cursor position to skip the whitespaces. Distinguish between the cases
                    // when the user adds or removes a digit. In the former case, the trimmed version
                    // of the previous text is shorted than the trimmed current text.
                    var cursorPosition = if (trimmedPreviousText.length < trimmedText.length)
                        when (val it = binding.combov2PinEntryEdit.selectionStart) {
                            // In these cases, we shift the cursor position, since we just entered the
                            // first digit of the next digit group, and the input has been adjusted to
                            // automatically include a whitespace. For example, we already had entered
                            // digits "123". The user entered the fourth digit, yielding "1234". The
                            // code above turned this into "123 4".
                            4, 8 ->
                                it + 1

                            else ->
                                it
                        }
                    else
                        when (val it = binding.combov2PinEntryEdit.selectionStart) {
                            // Similar to the block above, but in reverse: At these positions, removing
                            // the digit will also remove the automatically inserted whitespace, so we
                            // have to skip that one. For example, previously, the text on screen was
                            // "123 4", now we press backspace, and get "123 ". The code above turns
                            // this into "123".
                            4, 8 ->
                                it - 1

                            else ->
                                it
                        }

                    // Failsafe in case the calculations above are off for some reason. This is not
                    // clean; however, it is better than letting all of AndroidAPS crash.
                    if (cursorPosition > processedText.length) {
                        aapsLogger.warn(
                            LTag.PUMP,
                            "Incorrect cursor position $cursorPosition (processed text length ${processedText.length}); fixing"
                        )
                        cursorPosition = processedText.length
                    }

                    binding.combov2PinEntryEdit.setText(processedText)
                    binding.combov2PinEntryEdit.setSelection(cursorPosition)
                    binding.combov2PinEntryEdit.addTextChangedListener(this)
                }
            }
        }
        binding.combov2PinEntryEdit.addTextChangedListener(pinTextWatcher)

        binding.combov2EnterPin.setOnClickListener {
            // We need to skip whitespaces since the
            // TextWatcher above inserts some.
            val pinString = binding.combov2PinEntryEdit.text.replace(whitespaceRemovalRegex, "")
            if (pinString.length != PAIRING_PIN_SIZE) {
                ToastUtils.showToastInUiThread(this, rh.gs(R.string.combov2_pairing_invalid_pin_length, PAIRING_PIN_SIZE, pinString.length))
                return@setOnClickListener
            }
            runBlocking {
                val pin = PairingPIN(pinString.map { it - '0' }.toIntArray())
                combov2Plugin.providePairingPIN(pin)
            }
        }

        binding.combov2StartPairing.setOnClickListener {
            combov2Plugin.startPairing()
        }

        binding.combov2CancelPairing.setOnClickListener {
            OKDialog.showConfirmation(this, "Confirm pairing cancellation", "Do you really want to cancel pairing?", ok = Runnable {
                combov2Plugin.cancelPairing()
            })
        }

        combov2Plugin.getPairingProgressFlow()
            .onEach { progressReport ->
                val stage = progressReport.stage

                binding.combov2PairingSectionInitial.visibility =
                    if (stage == BasicProgressStage.Idle) View.VISIBLE else View.GONE
                binding.combov2PairingSectionFinished.visibility =
                    if (stage == BasicProgressStage.Finished) View.VISIBLE else View.GONE
                binding.combov2PairingSectionAborted.visibility =
                    if (stage is BasicProgressStage.Aborted) View.VISIBLE else View.GONE
                binding.combov2PairingSectionMain.visibility = when (stage) {
                    BasicProgressStage.Idle,
                    BasicProgressStage.Finished,
                    is BasicProgressStage.Aborted -> View.GONE

                    else                          -> View.VISIBLE
                }

                if (stage is BasicProgressStage.Aborted) {
                    binding.combov2PairingAbortedReasonText.text = when (stage) {
                        is BasicProgressStage.Cancelled -> rh.gs(R.string.combov2_pairing_cancelled)
                        is BasicProgressStage.Timeout   -> rh.gs(R.string.combov2_pairing_combo_scan_timeout_reached)
                        is BasicProgressStage.Error     -> rh.gs(R.string.combov2_pairing_failed_due_to_error, stage.cause.toString())
                        else                            -> rh.gs(R.string.combov2_pairing_aborted_unknown_reasons)
                    }
                }

                binding.combov2CurrentPairingStepDesc.text = when (stage) {
                    BasicProgressStage.ScanningForPumpStage           ->
                        rh.gs(R.string.combov2_scanning_for_pump)

                    is BasicProgressStage.EstablishingBtConnection    -> {
                        rh.gs(
                            R.string.combov2_establishing_bt_connection,
                            stage.currentAttemptNr
                        )
                    }

                    BasicProgressStage.PerformingConnectionHandshake  ->
                        rh.gs(R.string.combov2_pairing_performing_handshake)

                    BasicProgressStage.ComboPairingKeyAndPinRequested ->
                        rh.gs(R.string.combov2_pairing_pump_requests_pin)

                    BasicProgressStage.ComboPairingFinishing          ->
                        rh.gs(R.string.combov2_pairing_finishing)

                    else                                              -> ""
                }

                if (stage == BasicProgressStage.ComboPairingKeyAndPinRequested) {
                    binding.combov2PinEntryUi.visibility = View.VISIBLE
                } else
                    binding.combov2PinEntryUi.visibility = View.GONE

                // Scanning for the pump can take a long time and happens at the
                // beginning, so set the progress bar to indeterminate during that
                // time to show _something_ to the user.
                binding.combov2PairingProgressBar.isIndeterminate =
                    (stage == BasicProgressStage.ScanningForPumpStage)

                binding.combov2PairingProgressBar.progress = (progressReport.overallProgress * 100).toInt()
            }
            .launchIn(lifecycleScope)

        combov2Plugin.previousPairingAttemptFailedFlow
            .onEach { previousAttemptFailed ->
                binding.combov2PinFailureIndicator.visibility =
                    if (previousAttemptFailed) View.VISIBLE else View.GONE
            }
            .launchIn(lifecycleScope)
    }
}
