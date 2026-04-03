package app.aaps.plugins.main.general.nfcCommands

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.ui.dragHelpers.SimpleItemTouchHelperCallback
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.NfccommandsBuildFragmentBinding
import app.aaps.plugins.main.databinding.NfccommandsCommandItemBinding
import com.google.android.material.chip.Chip

class NfcBuildFragment : Fragment() {
    private var _binding: NfccommandsBuildFragmentBinding? = null
    private val binding get() = _binding!!

    private val categories = NfcCategories.build()
    private val chain = mutableListOf<String>()
    private lateinit var chainAdapter: NfcChainAdapter

    private var selectedCategoryIndex = -1
    private var selectedCommandIndex = -1
    private var selectedCommand: NfcUiCommand? = null
    private val commandRows = mutableListOf<View>()

    // Arg state
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

    companion object {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = NfccommandsBuildFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        restoreState(savedInstanceState)
        // Snap saved basal durations to the connected pump's valid step size.
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
        val savedChain = savedState.getStringArrayList(KEY_CHAIN)
        if (savedChain != null) {
            chain.clear()
            chain.addAll(savedChain)
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

    private fun setupCategoryChips() {
        val chipGroup = binding.categoryChips
        categories.forEachIndexed { index, cat ->
            val chip =
                Chip(requireContext()).apply {
                    text = getString(cat.labelResId)
                    isCheckable = true
                    isChecked = index == selectedCategoryIndex
                }
            chipGroup.addView(chip)
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
            NfcChainAdapter(chain) { pos ->
                chain.removeAt(pos)
                chainAdapter.notifyItemRemoved(pos)
                if (pos < chain.size) chainAdapter.notifyItemRangeChanged(pos, chain.size - pos)
                updateChainVisibility()
            }
        binding.chainRecycler.layoutManager = LinearLayoutManager(requireContext())
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
                (parentFragment as? NfcCommandsFragment)?.startWriteMode(chain.toList(), tagName)
            }
        }
    }

    private fun setupDocInfoButton() {
        binding.docInfoButton.setOnClickListener {
            val idx = selectedCategoryIndex.takeIf { it >= 0 } ?: return@setOnClickListener
            val category = categories[idx]
            val url = getString(R.string.nfccommands_doc_base_url) + getString(category.docAnchorResId)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun setupArgPanels() {
        // Pump disconnect presets
        listOf(
            15 to R.string.nfccommands_duration_15m,
            30 to R.string.nfccommands_duration_30m,
            60 to R.string.nfccommands_duration_1h,
            120 to R.string.nfccommands_duration_2h,
            180 to R.string.nfccommands_duration_3h,
        ).forEach { (min, labelRes) ->
            val chip =
                Chip(requireContext()).apply {
                    text = getString(labelRes)
                    isCheckable = false
                }
            chip.setOnClickListener {
                pumpDisconnectMinutes = min
                updatePumpDisconnectDisplay()
                updatePreview()
            }
            binding.pumpDisconnectPresets.addView(chip)
        }

        // Pump disconnect stepper
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

        // Suspend presets
        listOf(
            30 to R.string.nfccommands_duration_30m,
            60 to R.string.nfccommands_duration_1h,
            120 to R.string.nfccommands_duration_2h,
            240 to R.string.nfccommands_duration_4h,
            480 to R.string.nfccommands_duration_8h,
        ).forEach { (min, labelRes) ->
            val chip =
                Chip(requireContext()).apply {
                    text = getString(labelRes)
                    isCheckable = false
                }
            chip.setOnClickListener {
                suspendMinutes = min
                updateSuspendDisplay()
                updatePreview()
            }
            binding.suspendPresets.addView(chip)
        }

        // Suspend stepper
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

        // Bolus stepper
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

        // Basal abs stepper
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
            val s = basalDurationStep
            basalAbsDuration = maxOf(s, basalAbsDuration - s)
            updateBasalAbsDurDisplay()
            updatePreview()
        }
        binding.basalAbsDurPlus.setOnClickListener {
            val s = basalDurationStep
            basalAbsDuration = minOf(480, basalAbsDuration + s)
            updateBasalAbsDurDisplay()
            updatePreview()
        }

        // Basal pct stepper
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
            val s = basalDurationStep
            basalPctDuration = maxOf(s, basalPctDuration - s)
            updateBasalPctDurDisplay()
            updatePreview()
        }
        binding.basalPctDurPlus.setOnClickListener {
            val s = basalDurationStep
            basalPctDuration = minOf(480, basalPctDuration + s)
            updateBasalPctDurDisplay()
            updatePreview()
        }

        // Extended stepper
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

        // Carbs stepper
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

        // Profile stepper
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

        // Add to chain
        binding.addToChain.setOnClickListener {
            val cmd =
                binding.previewText.text
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@setOnClickListener
            chain.add(cmd)
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
                onCommandRowSelected(index, command, category)
            }
            commandRows.add(rowBinding.root)
            binding.commandList.addView(rowBinding.root)
        }
    }

    private fun onCommandRowSelected(
        index: Int,
        command: NfcUiCommand,
        category: NfcUiCategory,
    ) {
        selectedCommandIndex = index
        selectedCommand = command

        // Update row visuals
        commandRows.forEachIndexed { i, row ->
            val label = row.findViewById<TextView>(R.id.command_label)
            val icon = row.findViewById<ImageView>(R.id.command_selected_icon)
            label.setTypeface(null, if (i == index) Typeface.BOLD else Typeface.NORMAL)
            icon.visibility = if (i == index) View.VISIBLE else View.GONE
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
        val cmd = buildCurrentCommand()
        if (cmd != null) {
            binding.previewText.setText(cmd)
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
        chain.clear()
        chainAdapter.notifyDataSetChanged()
        updateChainVisibility()
    }

    // Display refresh helpers
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
        binding.extUnitsValue.text = getString(R.string.nfccommands_bolus_units).format(extendedUnits)
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

    private fun Double.roundTo2() = Math.round(this * 100) / 100.0

    /**
     * Returns the active pump's TBR duration step in minutes.
     * Falls back to 60 if the parent is not yet attached or the pump is unknown.
     */
    private val basalDurationStep: Int
        get() = (parentFragment as? NfcCommandsFragment)?.pumpBasalDurationStep() ?: 60

    /**
     * Rounds [value] up to the nearest positive multiple of [step],
     * with a minimum of [step] itself.
     */
    private fun snapToStep(
        value: Int,
        step: Int,
    ): Int = if (value % step == 0) maxOf(step, value) else maxOf(step, ((value / step) + 1) * step)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
