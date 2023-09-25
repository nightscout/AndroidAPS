package info.nightscout.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.BolusCalculatorResult
import com.google.gson.Gson
import dagger.android.support.DaggerDialogFragment
import info.nightscout.ui.R
import info.nightscout.ui.databinding.DialogWizardinfoBinding
import javax.inject.Inject

class WizardInfoDialog : DaggerDialogFragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var dateUtil: DateUtil

    private lateinit var data: BolusCalculatorResult

    private var _binding: DialogWizardinfoBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        (savedInstanceState ?: arguments)?.let {
            it.getString("data")?.let { str ->
                data = Gson().fromJson(str, BolusCalculatorResult::class.java)
            }
        }
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        _binding = DialogWizardinfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("data", Gson().toJson(data).toString())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.close.setOnClickListener { dismiss() }
        val bgString = profileUtil.fromMgdlToStringInUnits(data.glucoseValue)
        val isf = profileUtil.fromMgdlToUnits(data.isf)
        val trend = profileUtil.fromMgdlToStringInUnits(data.glucoseTrend * 3)
        // BG
        binding.bg.text = rh.gs(R.string.format_bg_isf, bgString, isf)
        binding.bgInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.glucoseInsulin)
        binding.bgCheckbox.isChecked = data.wasGlucoseUsed
        binding.ttCheckbox.isChecked = data.wasTempTargetUsed
        // Trend
        binding.bgTrend.text = trend
        binding.bgTrendInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.trendInsulin)
        binding.bgTrendCheckbox.isChecked = data.wasTrendUsed
        // COB
        binding.cob.text = rh.gs(R.string.format_cob_ic, data.cob, data.ic)
        binding.cobInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.cobInsulin)
        binding.cobCheckbox.isChecked = data.wasCOBUsed
        // Bolus IOB
        binding.bolusIobInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.bolusIOB)
        binding.bolusIobCheckbox.isChecked = data.wasBolusIOBUsed
        // Basal IOB
        binding.basalIobInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.basalIOB)
        binding.basalIobCheckbox.isChecked = data.wasBasalIOBUsed
        // Superbolus
        binding.sbInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.superbolusInsulin)
        binding.sbCheckbox.isChecked = data.wasSuperbolusUsed
        // Carbs
        binding.carbs.text = rh.gs(R.string.format_carbs_ic, data.carbs, data.ic)
        binding.carbsInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.carbsInsulin)
        // Correction
        binding.correctionInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.otherCorrection)
        // Profile
        binding.profile.text = data.profileName
        // Notes
        binding.notes.text = data.note
        // Percentage
        binding.percentUsed.text = rh.gs(app.aaps.core.ui.R.string.format_percent, data.percentageCorrection)
        // Total
        binding.totalInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, data.totalInsulin)
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