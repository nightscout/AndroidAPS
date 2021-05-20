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
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.databinding.DialogWizardinfoBinding
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class WizardInfoDialog : DaggerDialogFragment() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction

    private lateinit var data: BolusCalculatorResult

    fun setData(bolusCalculatorResult: BolusCalculatorResult) {
        this.data = bolusCalculatorResult
    }

    private var _binding: DialogWizardinfoBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        _binding = DialogWizardinfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.close.setOnClickListener { dismiss() }
        val units = profileFunction.getUnits()
        val bgString = Profile.toUnitsString(data.glucoseValue, data.glucoseValue * Constants.MGDL_TO_MMOLL, units)
        // BG
        binding.bg.text = resourceHelper.gs(R.string.format_bg_isf, bgString, data.isf)
        binding.bginsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.glucoseInsulin)
        binding.bgcheckbox.isChecked = data.wasGlucoseUsed
        binding.ttcheckbox.isChecked = data.wasTempTargetUsed
        // Trend
        binding.bgtrend.text = DecimalFormatter.to1Decimal(data.glucoseTrend)
        binding.bgtrendinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.trendInsulin)
        binding.bgtrendcheckbox.isChecked = data.wasTrendUsed
        // COB
        binding.cob.text = resourceHelper.gs(R.string.format_cob_ic, data.cob, data.ic)
        binding.cobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.cobInsulin)
        binding.cobcheckbox.isChecked = data.wasCOBUsed
        // Bolus IOB
        binding.bolusiobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.bolusIOB)
        binding.bolusiobcheckbox.isChecked = data.wasBolusIOBUsed
        // Basal IOB
        binding.basaliobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.basalIOB)
        binding.basaliobcheckbox.isChecked = data.wasBasalIOBUsed
        // Superbolus
        binding.sbinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.superbolusInsulin)
        binding.sbcheckbox.isChecked = data.wasSuperbolusUsed
        // Carbs
        binding.carbs.text = resourceHelper.gs(R.string.format_carbs_ic, data.carbs, data.ic)
        binding.carbsinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.carbsInsulin)
        // Correction
        binding.correctioninsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.otherCorrection)
        // Profile
        binding.profile.text = data.profileName
        // Notes
        binding.notes.text = data.note
        // Percentage
        binding.percentUsed.text = resourceHelper.gs(R.string.format_percent, data.percentageCorrection)
        // Total
        binding.totalinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, data.totalInsulin)
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
