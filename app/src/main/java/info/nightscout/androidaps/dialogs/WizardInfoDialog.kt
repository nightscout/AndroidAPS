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
import info.nightscout.androidaps.databinding.DialogWizardinfoBinding
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject

class WizardInfoDialog : DaggerDialogFragment() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction

    private var json: JSONObject? = null

    fun setData(json: JSONObject) {
        this.json = json
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
        val bgString =
            if (units == Constants.MGDL) DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "bg"))
            else DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "bg"))
        // BG
        binding.bg.text = resourceHelper.gs(R.string.format_bg_isf, bgString, JsonHelper.safeGetDouble(json, "isf"))
        binding.bginsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulinbg"))
        binding.bgcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "insulinbgused")
        binding.ttcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "ttused")
        // Trend
        binding.bgtrend.text = JsonHelper.safeGetString(json, "trend")
        binding.bgtrendinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulintrend"))
        binding.bgtrendcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "trendused")
        // COB
        binding.cob.text = resourceHelper.gs(R.string.format_cob_ic, JsonHelper.safeGetDouble(json, "cob"), JsonHelper.safeGetDouble(json, "ic"))
        binding.cobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulincob"))
        binding.cobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "cobused")
        // Bolus IOB
        binding.bolusiobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "bolusiob"))
        binding.bolusiobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "bolusiobused")
        // Basal IOB
        binding.basaliobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "basaliob"))
        binding.basaliobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "basaliobused")
        // Superbolus
        binding.sbinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulinsuperbolus"))
        binding.sbcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "superbolusused")
        // Carbs
        binding.carbs.text = resourceHelper.gs(R.string.format_carbs_ic, JsonHelper.safeGetDouble(json, "carbs"), JsonHelper.safeGetDouble(json, "ic"))
        binding.carbsinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulincarbs"))
        // Correction
        binding.correctioninsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "othercorrection"))
        // Profile
        binding.profile.text = JsonHelper.safeGetString(json, "profile")
        // Notes
        binding.notes.text = JsonHelper.safeGetString(json, "notes")
        // Percentage
        binding.percentUsed.text = resourceHelper.gs(R.string.format_percent, (JsonHelper.safeGetInt(json, "percentageCorrection", 100)))
        // Total
        binding.totalinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulin"))
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
