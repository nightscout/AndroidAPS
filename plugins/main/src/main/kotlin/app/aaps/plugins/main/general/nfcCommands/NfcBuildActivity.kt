package app.aaps.plugins.main.general.nfcCommands

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.dragHelpers.SimpleItemTouchHelperCallback
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsBuildFragmentBinding
import app.aaps.plugins.main.databinding.NfccommandsCommandItemBinding
import app.aaps.plugins.main.databinding.NfccommandsWriteDialogBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.android.AndroidInjection
import javax.inject.Inject

open class NfcBuildActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var plugin: NfcCommandsPlugin

    var nfcAdapter: NfcAdapter? = null

    private lateinit var binding: NfccommandsBuildFragmentBinding

    private val categories = NfcCategories.build()
    private val chain = mutableListOf<String>()
    private lateinit var chainAdapter: NfcChainAdapter

    private var selectedCategoryIndex = -1
    private var selectedCommandIndex = -1
    private var selectedCommand: NfcUiCommand? = null
    private val commandRows = mutableListOf<View>()

    private var pumpDisconnectMinutes = 30
    private var suspendMinutes = 60
    private var bolusUnits = 1.00
    private var mealBolus = false
    private var basalAbsRate = 1.00
    private var basalAbsDuration = 30
    private var basalPct = 100
    private var basalPctDuration = 30
    private var extendedUnits = 1.00
    private var extendedDuration = 30
    private var carbsGrams = 20
    private var profileIndex = 1
    private var profileWithPct = false
    private var profilePct = 100

    @Volatile private var isWritingMode = false
    private var pendingCommands: List<String> = emptyList()
    private var pendingTagName = ""
    private var pendingTag: Tag? = null
    private var writeDialog: AlertDialog? = null
    private var pulseAnimSet: AnimatorSet? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readerModeDisableRunnable: Runnable? = null

    companion object {
        private const val POST_WRITE_READER_MODE_HOLD_MS = 3_000L
        private const val KEY_CATEGORY = "sel_cat"
        private const val KEY_COMMAND = "sel_cmd"
        private const val KEY_CHAIN = "chain"
        private const val KEY_PUMP_DISCONNECT = "pump_disconnect_min"
        private const val KEY_SUSPEND = "suspend_min"
        private const val KEY_BOLUS = "bolus_u"
        private const val KEY_MEAL = "meal"
        private const val KEY_BASAL_ABS = "basal_abs"
        private const val KEY_BASAL_ABS_DUR = "basal_abs_dur"
        private const val KEY_BASAL_PCT = "basal_pct"
        private const val KEY_BASAL_PCT_DUR = "basal_pct_dur"
        private const val KEY_EXT_UNITS = "ext_u"
        private const val KEY_EXT_DUR = "ext_dur"
        private const val KEY_CARBS = "carbs_g"
        private const val KEY_PROFILE_IDX = "prof_idx"
        private const val KEY_PROFILE_PCT_ON = "prof_pct_on"
        private const val KEY_PROFILE_PCT = "prof_pct"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        binding = NfccommandsBuildFragmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        restoreState(savedInstanceState)
        val step = basalDurationStep
        basalAbsDuration = snapToStep(basalAbsDuration, step)
        basalPctDuration = snapToStep(basalPctDuration, step)

        setupCategoryChips()
        setupChainRecycler()
        setupDocInfoButton()
        setupArgPanels()

        if (selectedCategoryIndex >= 0) {
            buildCommandRows(categories[selectedCategoryIndex])
            binding.commandListCard.visibility = View.VISIBLE
        }
        if (selectedCommand != null) {
            showArgPanel(selectedCommand!!.argType)
            updatePreview()
        }
        updateChainVisibility()
        refreshArgDisplays()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CATEGORY, selectedCategoryIndex)
        outState.putInt(KEY_COMMAND, selectedCommandIndex)
        outState.putStringArrayList(KEY_CHAIN, ArrayList(chain))
        outState.putInt(KEY_PUMP_DISCONNECT, pumpDisconnectMinutes)
        outState.putInt(KEY_SUSPEND, suspendMinutes)
        outState.putDouble(KEY_BOLUS, bolusUnits)
        outState.putBoolean(KEY_MEAL, mealBolus)
        outState.putDouble(KEY_BASAL_ABS, basalAbsRate)
        outState.putInt(KEY_BASAL_ABS_DUR, basalAbsDuration)
        outState.putInt(KEY_BASAL_PCT, basalPct)
        outState.putInt(KEY_BASAL_PCT_DUR, basalPctDuration)
        outState.putDouble(KEY_EXT_UNITS, extendedUnits)
        outState.putInt(KEY_EXT_DUR, extendedDuration)
        outState.putInt(KEY_CARBS, carbsGrams)
        outState.putInt(KEY_PROFILE_IDX, profileIndex)
        outState.putBoolean(KEY_PROFILE_PCT_ON, profileWithPct)
        outState.putInt(KEY_PROFILE_PCT, profilePct)
    }

    private fun restoreState(savedState: Bundle?) {
        if (savedState == null) return
        selectedCategoryIndex = savedState.getInt(KEY_CATEGORY, -1)
        selectedCommandIndex = savedState.getInt(KEY_COMMAND, -1)
        savedState.getStringArrayList(KEY_CHAIN)?.let {
            chain.clear()
            chain.addAll(it)
        }
        pumpDisconnectMinutes = savedState.getInt(KEY_PUMP_DISCONNECT, 30)
        suspendMinutes = savedState.getInt(KEY_SUSPEND, 60)
        bolusUnits = savedState.getDouble(KEY_BOLUS, 1.00)
        mealBolus = savedState.getBoolean(KEY_MEAL, false)
        basalAbsRate = savedState.getDouble(KEY_BASAL_ABS, 1.00)
        basalAbsDuration = savedState.getInt(KEY_BASAL_ABS_DUR, 30)
        basalPct = savedState.getInt(KEY_BASAL_PCT, 100)
        basalPctDuration = savedState.getInt(KEY_BASAL_PCT_DUR, 30)
        extendedUnits = savedState.getDouble(KEY_EXT_UNITS, 1.00)
        extendedDuration = savedState.getInt(KEY_EXT_DUR, 30)
        carbsGrams = savedState.getInt(KEY_CARBS, 20)
        profileIndex = savedState.getInt(KEY_PROFILE_IDX, 1)
        profileWithPct = savedState.getBoolean(KEY_PROFILE_PCT_ON, false)
        profilePct = savedState.getInt(KEY_PROFILE_PCT, 100)

        if (selectedCategoryIndex >= 0 && selectedCommandIndex >= 0) {
            selectedCommand = categories[selectedCategoryIndex].commands.getOrNull(selectedCommandIndex)
        }
    }

    fun startWriteMode(
        commands: List<String>,
        tagName: String = "",
    ) {
        if (commands.isEmpty()) return
        val adapter = nfcAdapter
        if (adapter == null) {
            showWriteResult(getString(R.string.nfccommands_nfc_not_supported))
            return
        }
        pendingCommands = commands
        pendingTagName = tagName
        isWritingMode = true
        showWriteDialog(commands)
        adapter.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null,
        )
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!isWritingMode) return
        pendingTag = tag
        val commands = pendingCommands
        if (commands.isEmpty()) {
            postUi(Runnable { disableWritingMode() })
            return
        }

        val name = pendingTagName.ifBlank { commands.first() }
        val issued = issueToken(commands)
        val success = buildAndWriteNdef(tag, issued.token)
        val message = buildWriteMessage(success)
        persistWriteAttempt(name, success, message)
        if (success) {
            persistWrittenTag(issued, name, commands)
        }

        postUi(
            Runnable {
                if (success) {
                    clearChain()
                    showWriteResult(message)
                    disableWritingMode(delayReaderModeDisable = true)
                } else {
                    showWriteResult(message)
                    disableWritingMode()
                }
            },
        )
    }

    fun disableWritingMode(delayReaderModeDisable: Boolean = false) {
        isWritingMode = false
        pulseAnimSet?.cancel()
        pulseAnimSet = null
        writeDialog?.dismiss()
        writeDialog = null
        if (delayReaderModeDisable) {
            scheduleReaderModeDisable()
        } else {
            cancelScheduledReaderModeDisable()
            nfcAdapter?.disableReaderMode(this)
        }
    }

    override fun onStop() {
        super.onStop()
        cancelScheduledReaderModeDisable()
        if (isWritingMode) {
            isWritingMode = false
            pulseAnimSet?.cancel()
            pulseAnimSet = null
            writeDialog?.dismiss()
            writeDialog = null
        }
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelScheduledReaderModeDisable()
        pulseAnimSet?.cancel()
        writeDialog?.dismiss()
    }

    open fun issueToken(commands: List<String>): NfcIssuedToken =
        NfcTokenSupport.issueToken(this, commands, tagUid = NfcTokenSupport.tagUidHex(pendingTag?.id))

    open fun buildAndWriteNdef(
        tag: Tag,
        token: String,
    ): Boolean {
        val record = NdefRecord.createMime(NfcTokenSupport.MIME_TYPE, token.toByteArray())
        val message = NdefMessage(arrayOf(record))
        val ndefFormatable = NdefFormatable.get(tag)
        val ndef = Ndef.get(tag)
        return when {
            ndefFormatable != null -> writeNdefFormatable(ndefFormatable, message)
            ndef != null -> writeNdef(ndef, message)
            else -> {
                aapsLogger.error(LTag.NFC, "Tag supports neither Ndef nor NdefFormatable. Techs: ${tag.techList.joinToString()}")
                false
            }
        }
    }

    open fun buildWriteMessage(success: Boolean): String =
        if (success) getString(R.string.nfccommands_tag_written) else getString(R.string.nfccommands_tag_write_error)

    open fun persistWriteAttempt(
        name: String,
        success: Boolean,
        message: String,
    ) {
        NfcTokenSupport.appendLogEntry(
            this,
            NfcLogEntry(
                timestamp = System.currentTimeMillis(),
                tagName = name,
                action = "WRITE",
                success = success,
                message = message,
            ),
        )
    }

    open fun persistWrittenTag(
        token: NfcIssuedToken,
        name: String,
        commands: List<String>,
    ) {
        NfcTokenSupport.saveCreatedTag(
            this,
            NfcCreatedTag(
                id = token.tokenId,
                name = name,
                commands = commands,
                token = token.token,
                createdAtMillis = token.issuedAtMillis,
                expiresAtMillis = token.expiresAtMillis,
            ),
        )
    }

    open fun showWriteDialog(commands: List<String>) {
        val dialogView = NfccommandsWriteDialogBinding.inflate(layoutInflater)
        val primaryColor =
            TypedValue()
                .also { theme.resolveAttribute(android.R.attr.colorPrimary, it, true) }
                .data

        dialogView.ring1.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primaryColor)
            }
        dialogView.ring2.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(3), primaryColor)
            }
        dialogView.ring3.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(2), primaryColor)
            }

        dialogView.writeSummary.text =
            commands
                .mapIndexed { index, command ->
                    getString(R.string.nfccommands_cascade_step_label, index + 1, command)
                }.joinToString("\n")

        val dialog =
            MaterialAlertDialogBuilder(this)
                .setView(dialogView.root)
                .setCancelable(false)
                .create()

        dialogView.cancelButton.setOnClickListener {
            disableWritingMode()
        }

        dialog.show()
        writeDialog = dialog
        startPulseAnimation(dialogView)
    }

    open fun showWriteResult(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    open fun scheduleReaderModeDisable() {
        cancelScheduledReaderModeDisable()
        val adapter = nfcAdapter ?: return
        val runnable = Runnable { adapter.disableReaderMode(this) }
        readerModeDisableRunnable = runnable
        mainHandler.postDelayed(runnable, POST_WRITE_READER_MODE_HOLD_MS)
    }

    open fun postUi(action: Runnable) {
        runOnUiThread(action)
    }

    open fun pumpBasalDurationStep(): Int = plugin.pumpBasalDurationStep()

    private fun setupCategoryChips() {
        categories.forEachIndexed { index, category ->
            val chip =
                Chip(this).apply {
                    text = getString(category.labelResId)
                    isCheckable = true
                    isChecked = index == selectedCategoryIndex
                }
            binding.categoryChips.addView(chip)
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedCategoryIndex = index
                    selectedCommandIndex = -1
                    selectedCommand = null
                    buildCommandRows(categories[index])
                    binding.commandListCard.visibility = View.VISIBLE
                    hideAllArgPanels()
                    binding.argsCard.visibility = View.GONE
                    binding.previewCard.visibility = View.GONE
                }
            }
        }
    }

    private fun setupChainRecycler() {
        chainAdapter =
            NfcChainAdapter(chain) { position ->
                chain.removeAt(position)
                chainAdapter.notifyItemRemoved(position)
                if (position < chain.size) chainAdapter.notifyItemRangeChanged(position, chain.size - position)
                updateChainVisibility()
            }
        binding.chainRecycler.layoutManager = LinearLayoutManager(this)
        binding.chainRecycler.adapter = chainAdapter
        binding.chainRecycler.isNestedScrollingEnabled = false

        val callback = SimpleItemTouchHelperCallback()
        val touchHelper = ItemTouchHelper(callback)
        chainAdapter.touchHelper = touchHelper
        touchHelper.attachToRecyclerView(binding.chainRecycler)

        binding.writeFab.setOnClickListener {
            if (chain.isNotEmpty()) {
                val tagName =
                    binding.tagName.text
                        ?.toString()
                        .orEmpty()
                requestWrite(chain.toList(), tagName)
            }
        }
    }

    open fun requestWrite(
        commands: List<String>,
        tagName: String,
    ) {
        if (tagName.isBlank()) {
            showBlankTagNameDialog(commands)
        } else {
            startWriteMode(commands, tagName)
        }
    }

    open fun showBlankTagNameDialog(commands: List<String>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nfccommands_blank_name_confirm_title)
            .setMessage(R.string.nfccommands_blank_name_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.nfccommands_blank_name_confirm_write_anyway) { _, _ ->
                startWriteMode(commands)
            }.show()
    }

    private fun setupDocInfoButton() {
        binding.docInfoButton.setOnClickListener {
            val index = selectedCategoryIndex.takeIf { it >= 0 } ?: return@setOnClickListener
            val category = categories[index]
            val url = getString(R.string.nfccommands_doc_base_url) + getString(category.docAnchorResId)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun setupArgPanels() {
        listOf(
            15 to R.string.nfccommands_duration_15m,
            30 to R.string.nfccommands_duration_30m,
            60 to R.string.nfccommands_duration_1h,
            120 to R.string.nfccommands_duration_2h,
            180 to R.string.nfccommands_duration_3h,
        ).forEach { (minutes, labelResId) ->
            val chip =
                Chip(this).apply {
                    text = getString(labelResId)
                    isCheckable = false
                }
            chip.setOnClickListener {
                pumpDisconnectMinutes = minutes
                updatePumpDisconnectDisplay()
                updatePreview()
            }
            binding.pumpDisconnectPresets.addView(chip)
        }

        binding.pumpDisconnectMinus.setOnClickListener {
            pumpDisconnectMinutes = maxOf(15, pumpDisconnectMinutes - 15)
            updatePumpDisconnectDisplay()
            updatePreview()
        }
        binding.pumpDisconnectPlus.setOnClickListener {
            pumpDisconnectMinutes = minOf(180, pumpDisconnectMinutes + 15)
            updatePumpDisconnectDisplay()
            updatePreview()
        }

        listOf(
            30 to R.string.nfccommands_duration_30m,
            60 to R.string.nfccommands_duration_1h,
            120 to R.string.nfccommands_duration_2h,
            240 to R.string.nfccommands_duration_4h,
            480 to R.string.nfccommands_duration_8h,
        ).forEach { (minutes, labelResId) ->
            val chip =
                Chip(this).apply {
                    text = getString(labelResId)
                    isCheckable = false
                }
            chip.setOnClickListener {
                suspendMinutes = minutes
                updateSuspendDisplay()
                updatePreview()
            }
            binding.suspendPresets.addView(chip)
        }

        binding.suspendMinus.setOnClickListener {
            suspendMinutes = maxOf(30, suspendMinutes - 30)
            updateSuspendDisplay()
            updatePreview()
        }
        binding.suspendPlus.setOnClickListener {
            suspendMinutes = minOf(480, suspendMinutes + 30)
            updateSuspendDisplay()
            updatePreview()
        }

        binding.bolusMinus.setOnClickListener {
            bolusUnits = maxOf(0.05, bolusUnits - 0.05).roundTo2()
            updateBolusDisplay()
            updatePreview()
        }
        binding.bolusPlus.setOnClickListener {
            bolusUnits = minOf(30.0, bolusUnits + 0.05).roundTo2()
            updateBolusDisplay()
            updatePreview()
        }
        binding.bolusMealCheck.setOnCheckedChangeListener { _, checked ->
            mealBolus = checked
            updatePreview()
        }

        binding.basalAbsMinus.setOnClickListener {
            basalAbsRate = maxOf(0.05, basalAbsRate - 0.05).roundTo2()
            updateBasalAbsDisplay()
            updatePreview()
        }
        binding.basalAbsPlus.setOnClickListener {
            basalAbsRate = minOf(30.0, basalAbsRate + 0.05).roundTo2()
            updateBasalAbsDisplay()
            updatePreview()
        }
        binding.basalAbsDurMinus.setOnClickListener {
            val step = basalDurationStep
            basalAbsDuration = maxOf(step, basalAbsDuration - step)
            updateBasalAbsDurDisplay()
            updatePreview()
        }
        binding.basalAbsDurPlus.setOnClickListener {
            val step = basalDurationStep
            basalAbsDuration = minOf(480, basalAbsDuration + step)
            updateBasalAbsDurDisplay()
            updatePreview()
        }

        binding.basalPctMinus.setOnClickListener {
            basalPct = maxOf(0, basalPct - 10)
            updateBasalPctDisplay()
            updatePreview()
        }
        binding.basalPctPlus.setOnClickListener {
            basalPct = minOf(200, basalPct + 10)
            updateBasalPctDisplay()
            updatePreview()
        }
        binding.basalPctDurMinus.setOnClickListener {
            val step = basalDurationStep
            basalPctDuration = maxOf(step, basalPctDuration - step)
            updateBasalPctDurDisplay()
            updatePreview()
        }
        binding.basalPctDurPlus.setOnClickListener {
            val step = basalDurationStep
            basalPctDuration = minOf(480, basalPctDuration + step)
            updateBasalPctDurDisplay()
            updatePreview()
        }

        binding.extUnitsMinus.setOnClickListener {
            extendedUnits = maxOf(0.05, extendedUnits - 0.05).roundTo2()
            updateExtDisplay()
            updatePreview()
        }
        binding.extUnitsPlus.setOnClickListener {
            extendedUnits = minOf(30.0, extendedUnits + 0.05).roundTo2()
            updateExtDisplay()
            updatePreview()
        }
        binding.extDurMinus.setOnClickListener {
            extendedDuration = maxOf(15, extendedDuration - 15)
            updateExtDurDisplay()
            updatePreview()
        }
        binding.extDurPlus.setOnClickListener {
            extendedDuration = minOf(480, extendedDuration + 15)
            updateExtDurDisplay()
            updatePreview()
        }

        binding.carbsMinus.setOnClickListener {
            carbsGrams = maxOf(5, carbsGrams - 5)
            updateCarbsDisplay()
            updatePreview()
        }
        binding.carbsPlus.setOnClickListener {
            carbsGrams = minOf(500, carbsGrams + 5)
            updateCarbsDisplay()
            updatePreview()
        }

        binding.profileIndexMinus.setOnClickListener {
            profileIndex = maxOf(1, profileIndex - 1)
            updateProfileDisplay()
            updatePreview()
        }
        binding.profileIndexPlus.setOnClickListener {
            profileIndex = minOf(20, profileIndex + 1)
            updateProfileDisplay()
            updatePreview()
        }
        binding.profileWithPct.setOnCheckedChangeListener { _, checked ->
            profileWithPct = checked
            binding.profilePctRow.visibility = if (checked) View.VISIBLE else View.GONE
            updatePreview()
        }
        binding.profilePctMinus.setOnClickListener {
            profilePct = maxOf(70, profilePct - 5)
            updateProfilePctDisplay()
            updatePreview()
        }
        binding.profilePctPlus.setOnClickListener {
            profilePct = minOf(200, profilePct + 5)
            updateProfilePctDisplay()
            updatePreview()
        }

        binding.addToChain.setOnClickListener {
            val command =
                binding.previewText.text
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@setOnClickListener
            chain.add(command)
            chainAdapter.notifyItemInserted(chain.size - 1)
            updateChainVisibility()
        }
    }

    private fun buildCommandRows(category: NfcUiCategory) {
        binding.commandList.removeAllViews()
        commandRows.clear()

        category.commands.forEachIndexed { index, command ->
            val rowBinding = NfccommandsCommandItemBinding.inflate(layoutInflater, binding.commandList, false)
            rowBinding.commandLabel.text = getString(command.displayLabelResId)
            if (index == selectedCommandIndex) {
                rowBinding.commandLabel.setTypeface(null, Typeface.BOLD)
                rowBinding.commandSelectedIcon.visibility = View.VISIBLE
            }
            rowBinding.root.setOnClickListener {
                onCommandRowSelected(index, command)
            }
            commandRows.add(rowBinding.root)
            binding.commandList.addView(rowBinding.root)
        }
    }

    private fun onCommandRowSelected(
        index: Int,
        command: NfcUiCommand,
    ) {
        selectedCommandIndex = index
        selectedCommand = command

        commandRows.forEachIndexed { rowIndex, row ->
            val label = row.findViewById<TextView>(R.id.command_label)
            val icon = row.findViewById<ImageView>(R.id.command_selected_icon)
            label.setTypeface(null, if (rowIndex == index) Typeface.BOLD else Typeface.NORMAL)
            icon.visibility = if (rowIndex == index) View.VISIBLE else View.GONE
        }

        showArgPanel(command.argType)
        updatePreview()
    }

    private fun showArgPanel(argType: ArgType) {
        hideAllArgPanels()
        binding.argsCard.visibility = View.VISIBLE
        when (argType) {
            ArgType.NONE -> binding.panelNone.visibility = View.VISIBLE
            ArgType.SUSPEND -> binding.panelSuspend.visibility = View.VISIBLE
            ArgType.PUMP_DISCONNECT -> binding.panelPumpDisconnect.visibility = View.VISIBLE
            ArgType.BOLUS -> binding.panelBolus.visibility = View.VISIBLE
            ArgType.BASAL_ABS -> binding.panelBasalAbs.visibility = View.VISIBLE
            ArgType.BASAL_PCT -> binding.panelBasalPct.visibility = View.VISIBLE
            ArgType.EXTENDED -> binding.panelExtended.visibility = View.VISIBLE
            ArgType.CARBS -> binding.panelCarbs.visibility = View.VISIBLE
            ArgType.PROFILE -> binding.panelProfile.visibility = View.VISIBLE
        }
        refreshArgDisplays()
    }

    private fun hideAllArgPanels() {
        binding.panelNone.visibility = View.GONE
        binding.panelSuspend.visibility = View.GONE
        binding.panelPumpDisconnect.visibility = View.GONE
        binding.panelBolus.visibility = View.GONE
        binding.panelBasalAbs.visibility = View.GONE
        binding.panelBasalPct.visibility = View.GONE
        binding.panelExtended.visibility = View.GONE
        binding.panelCarbs.visibility = View.GONE
        binding.panelProfile.visibility = View.GONE
    }

    private fun updatePreview() {
        val command = buildCurrentCommand()
        if (command != null) {
            binding.previewText.setText(command)
            binding.previewCard.visibility = View.VISIBLE
        } else {
            binding.previewCard.visibility = View.GONE
        }
    }

    private fun buildCurrentCommand(): String? {
        val command = selectedCommand ?: return null
        return when (command.argType) {
            ArgType.NONE -> NfcTokenSupport.buildCommand(command.template, "")
            ArgType.SUSPEND -> NfcTokenSupport.buildCommand(command.template, suspendMinutes.toString())
            ArgType.PUMP_DISCONNECT -> NfcTokenSupport.buildCommand(command.template, pumpDisconnectMinutes.toString())
            ArgType.BOLUS -> {
                val args = if (mealBolus) "%.2f MEAL".format(bolusUnits) else "%.2f".format(bolusUnits)
                NfcTokenSupport.buildCommand(command.template, args)
            }
            ArgType.BASAL_ABS -> NfcTokenSupport.buildCommand(command.template, "%.2f %d".format(basalAbsRate, basalAbsDuration))
            ArgType.BASAL_PCT -> NfcTokenSupport.buildCommand(command.template, "$basalPct% $basalPctDuration")
            ArgType.EXTENDED -> NfcTokenSupport.buildCommand(command.template, "%.2f %d".format(extendedUnits, extendedDuration))
            ArgType.CARBS -> NfcTokenSupport.buildCommand(command.template, carbsGrams.toString())
            ArgType.PROFILE -> {
                val args = if (profileWithPct) "$profileIndex $profilePct" else profileIndex.toString()
                NfcTokenSupport.buildCommand(command.template, args)
            }
        }
    }

    private fun updateChainVisibility() {
        val hasChain = chain.isNotEmpty()
        binding.chainTitle.visibility = if (hasChain) View.VISIBLE else View.GONE
        binding.chainRecycler.visibility = if (hasChain) View.VISIBLE else View.GONE
        binding.writeFab.visibility = if (hasChain) View.VISIBLE else View.GONE
    }

    fun clearChain() {
        if (!::binding.isInitialized || !::chainAdapter.isInitialized) return
        chain.clear()
        chainAdapter.notifyDataSetChanged()
        updateChainVisibility()
    }

    private fun refreshArgDisplays() {
        updatePumpDisconnectDisplay()
        updateSuspendDisplay()
        updateBolusDisplay()
        updateBasalAbsDisplay()
        updateBasalAbsDurDisplay()
        updateBasalPctDisplay()
        updateBasalPctDurDisplay()
        updateExtDisplay()
        updateExtDurDisplay()
        updateCarbsDisplay()
        updateProfileDisplay()
        updateProfilePctDisplay()

        binding.bolusMealCheck.isChecked = mealBolus
        binding.profileWithPct.isChecked = profileWithPct
        binding.profilePctRow.visibility = if (profileWithPct) View.VISIBLE else View.GONE
    }

    private fun updatePumpDisconnectDisplay() {
        binding.pumpDisconnectValue.text = getString(R.string.nfccommands_pump_disconnect_minutes, pumpDisconnectMinutes)
    }

    private fun updateSuspendDisplay() {
        binding.suspendValue.text = getString(R.string.nfccommands_suspend_minutes, suspendMinutes)
    }

    private fun updateBolusDisplay() {
        binding.bolusValue.text = getString(R.string.nfccommands_bolus_units, bolusUnits)
    }

    private fun updateBasalAbsDisplay() {
        binding.basalAbsValue.text = getString(R.string.nfccommands_basal_abs_value).format(basalAbsRate)
    }

    private fun updateBasalAbsDurDisplay() {
        binding.basalAbsDurValue.text = basalAbsDuration.toString()
    }

    private fun updateBasalPctDisplay() {
        binding.basalPctValue.text = getString(R.string.nfccommands_percent_value, basalPct)
    }

    private fun updateBasalPctDurDisplay() {
        binding.basalPctDurValue.text = basalPctDuration.toString()
    }

    private fun updateExtDisplay() {
        binding.extUnitsValue.text = getString(R.string.nfccommands_bolus_units, extendedUnits)
    }

    private fun updateExtDurDisplay() {
        binding.extDurValue.text = extendedDuration.toString()
    }

    private fun updateCarbsDisplay() {
        binding.carbsValue.text = getString(R.string.nfccommands_carbs_value, carbsGrams)
    }

    private fun updateProfileDisplay() {
        binding.profileIndexValue.text = profileIndex.toString()
    }

    private fun updateProfilePctDisplay() {
        binding.profilePctValue.text = getString(R.string.nfccommands_percent_value, profilePct)
    }

    private fun startPulseAnimation(dialogView: NfccommandsWriteDialogBinding) {
        fun pulseAnimation(
            view: View,
            startDelay: Long,
        ): AnimatorSet {
            val scaleX =
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    this.startDelay = startDelay
                }
            val scaleY =
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    this.startDelay = startDelay
                }
            val alpha =
                ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
                    duration = 900
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    this.startDelay = startDelay
                }
            return AnimatorSet().also { it.playTogether(scaleX, scaleY, alpha) }
        }

        val animation = AnimatorSet()
        animation.playTogether(
            pulseAnimation(dialogView.ring1, 0),
            pulseAnimation(dialogView.ring2, 200),
            pulseAnimation(dialogView.ring3, 400),
        )
        animation.start()
        pulseAnimSet = animation
    }

    private fun cancelScheduledReaderModeDisable() {
        readerModeDisableRunnable?.let { mainHandler.removeCallbacks(it) }
        readerModeDisableRunnable = null
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun writeNdef(
        ndef: Ndef,
        message: NdefMessage,
    ): Boolean =
        try {
            ndef.connect()
            try {
                ndef.writeNdefMessage(message)
                true
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NFC, "Failed to write NDEF tag", e)
            false
        }

    private fun writeNdefFormatable(
        formatable: NdefFormatable,
        message: NdefMessage,
    ): Boolean =
        try {
            formatable.connect()
            try {
                formatable.format(message)
                true
            } finally {
                formatable.close()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NFC, "Failed to format and write NDEF tag", e)
            false
        }

    private val basalDurationStep: Int
        get() = pumpBasalDurationStep()

    private fun snapToStep(
        value: Int,
        step: Int,
    ): Int = if (value % step == 0) maxOf(step, value) else maxOf(step, ((value / step) + 1) * step)

    private fun Double.roundTo2() = Math.round(this * 100) / 100.0
}
