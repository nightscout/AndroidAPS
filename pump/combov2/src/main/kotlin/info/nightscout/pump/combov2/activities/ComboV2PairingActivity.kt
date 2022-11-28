package info.nightscout.pump.combov2.activities

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.comboctl.base.BasicProgressStage
import info.nightscout.comboctl.base.PairingPIN
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R
import info.nightscout.pump.combov2.databinding.Combov2PairingActivityBinding
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ComboV2PairingActivity : DaggerAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var combov2Plugin: ComboV2Plugin

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
                Activity.RESULT_CANCELED -> {
                    aapsLogger.info(LTag.PUMP, "User rejected discovery request; cancelling pairing")
                    combov2Plugin.cancelPairing()
                }
                else -> Unit
            }
        }
        combov2Plugin.customDiscoveryActivityStartCallback = { intent ->
            startPairingActivityLauncher.launch(intent)
        }

        val binding: Combov2PairingActivityBinding = DataBindingUtil.setContentView(
            this, R.layout.combov2_pairing_activity)

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
        binding.combov2PinEntryEdit.addTextChangedListener(object : TextWatcher {
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
        })

        binding.combov2EnterPin.setOnClickListener {
            // We need to skip whitespaces since the
            // TextWatcher above inserts some.
            val pinString = binding.combov2PinEntryEdit.text.replace(whitespaceRemovalRegex, "")
            runBlocking {
                val PIN = PairingPIN(pinString.map { it - '0' }.toIntArray())
                combov2Plugin.providePairingPIN(PIN)
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

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                            else -> View.VISIBLE
                        }

                        if (stage is BasicProgressStage.Aborted) {
                            binding.combov2PairingAbortedReasonText.text = when (stage) {
                                is BasicProgressStage.Cancelled -> rh.gs(R.string.combov2_pairing_cancelled)
                                is BasicProgressStage.Timeout -> rh.gs(R.string.combov2_pairing_combo_scan_timeout_reached)
                                is BasicProgressStage.Error -> rh.gs(R.string.combov2_pairing_failed_due_to_error, stage.cause.toString())
                                else -> rh.gs(R.string.combov2_pairing_aborted_unknown_reasons)
                            }
                        }

                        binding.combov2CurrentPairingStepDesc.text = when (val progStage = stage) {
                            BasicProgressStage.ScanningForPumpStage ->
                                rh.gs(R.string.combov2_scanning_for_pump)

                            is BasicProgressStage.EstablishingBtConnection -> {
                                rh.gs(
                                    R.string.combov2_establishing_bt_connection,
                                    progStage.currentAttemptNr
                                )
                            }

                            BasicProgressStage.PerformingConnectionHandshake ->
                                rh.gs(R.string.combov2_pairing_performing_handshake)

                            BasicProgressStage.ComboPairingKeyAndPinRequested  ->
                                rh.gs(R.string.combov2_pairing_pump_requests_pin)

                            BasicProgressStage.ComboPairingFinishing ->
                                rh.gs(R.string.combov2_pairing_finishing)

                            else -> ""
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
                    .launchIn(this)

                combov2Plugin.previousPairingAttemptFailedFlow
                    .onEach { previousAttemptFailed ->
                        binding.combov2PinFailureIndicator.visibility =
                            if (previousAttemptFailed) View.VISIBLE else View.GONE
                    }
                    .launchIn(this)
            }
        }
    }

    override fun onBackPressed() {
        aapsLogger.info(LTag.PUMP, "User pressed the back button; cancelling any ongoing pairing")
        combov2Plugin.cancelPairing()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
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
            else -> Unit
        }

        super.onDestroy()
    }
}
