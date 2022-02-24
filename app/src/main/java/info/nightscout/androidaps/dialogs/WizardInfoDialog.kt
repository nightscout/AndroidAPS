package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.databinding.DialogWizardinfoBinding
import info.nightscout.androidaps.extensions.bolusCalculatorResultFromJson
import info.nightscout.androidaps.extensions.toJson
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject

class WizardInfoDialog : DaggerDialogFragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil

    private lateinit var data: BolusCalculatorResult

    fun setData(bolusCalculatorResult: BolusCalculatorResult) {
        this.data = bolusCalculatorResult
    }

    private var _binding: DialogWizardinfoBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        _binding = DialogWizardinfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("data")?.let { str ->
            val json = JSONObject(str).apply {
                put("mills", dateUtil.now()) // fake NS response
            }
            data = bolusCalculatorResultFromJson(json) ?: return
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("data", data.toJson(true, dateUtil).toString())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.close.setOnClickListener { dismiss() }
        val units = profileFunction.getUnits()
        val bgString = Profile.toUnitsString(data.glucoseValue, data.glucoseValue * Constants.MGDL_TO_MMOLL, units)
        val isf = Profile.toUnits(data.isf, data.isf * Constants.MGDL_TO_MMOLL, units)
        val trend = Profile.toUnitsString(data.glucoseTrend * 3, data.glucoseTrend * 3 * Constants.MGDL_TO_MMOLL, units)
        // BG
        binding.bg.text = rh.gs(R.string.format_bg_isf, bgString, isf)
        binding.bgInsulin.text = rh.gs(R.string.formatinsulinunits, data.glucoseInsulin)
        binding.bgCheckbox.isChecked = data.wasGlucoseUsed
        binding.ttCheckbox.isChecked = data.wasTempTargetUsed
        // Trend
        binding.bgTrend.text = trend
        binding.bgTrendInsulin.text = rh.gs(R.string.formatinsulinunits, data.trendInsulin)
        binding.bgTrendCheckbox.isChecked = data.wasTrendUsed
        // COB
        binding.cob.text = rh.gs(R.string.format_cob_ic, data.cob, data.ic)
        binding.cobInsulin.text = rh.gs(R.string.formatinsulinunits, data.cobInsulin)
        binding.cobCheckbox.isChecked = data.wasCOBUsed
        // Bolus IOB
        binding.bolusIobInsulin.text = rh.gs(R.string.formatinsulinunits, data.bolusIOB)
        binding.bolusIobCheckbox.isChecked = data.wasBolusIOBUsed
        // Basal IOB
        binding.basalIobInsulin.text = rh.gs(R.string.formatinsulinunits, data.basalIOB)
        binding.basalIobCheckbox.isChecked = data.wasBasalIOBUsed
        // Superbolus
        binding.sbinsulin.text = rh.gs(R.string.formatinsulinunits, data.superbolusInsulin)
        binding.sbCheckbox.isChecked = data.wasSuperbolusUsed
        // Carbs
        binding.carbs.text = rh.gs(R.string.format_carbs_ic, data.carbs, data.ic)
        binding.carbsinsulin.text = rh.gs(R.string.formatinsulinunits, data.carbsInsulin)
        // Correction
        binding.correctioninsulin.text = rh.gs(R.string.formatinsulinunits, data.otherCorrection)
        // Profile
        binding.profile.text = data.profileName
        // Notes
        binding.notes.text = data.note
        // Percentage
        binding.percentUsed.text = rh.gs(R.string.format_percent, data.percentageCorrection)
        // Total
        binding.totalinsulin.text = rh.gs(R.string.formatinsulinunits, data.totalInsulin)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
