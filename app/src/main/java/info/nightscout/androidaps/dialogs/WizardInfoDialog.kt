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
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_wizardinfo.*
import org.json.JSONObject
import javax.inject.Inject

class WizardInfoDialog : DaggerDialogFragment() {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction

    private var json: JSONObject? = null

    fun setData(json: JSONObject) {
        this.json = json
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        return inflater.inflate(R.layout.dialog_wizardinfo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.setOnClickListener { dismiss() }
        val units = profileFunction.getUnits()
        val bgString =
            if (units == Constants.MGDL) DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "bg"))
            else DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "bg"))
        // BG
        treatments_wizard_bg.text = resourceHelper.gs(R.string.format_bg_isf, bgString, JsonHelper.safeGetDouble(json, "isf"))
        treatments_wizard_bginsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulinbg"))
        treatments_wizard_bgcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "insulinbgused")
        treatments_wizard_ttcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "ttused")
        // Trend
        treatments_wizard_bgtrend.text = JsonHelper.safeGetString(json, "trend")
        treatments_wizard_bgtrendinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulintrend"))
        treatments_wizard_bgtrendcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "trendused")
        // COB
        treatments_wizard_cob.text = resourceHelper.gs(R.string.format_cob_ic, JsonHelper.safeGetDouble(json, "cob"), JsonHelper.safeGetDouble(json, "ic"))
        treatments_wizard_cobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulincob"))
        treatments_wizard_cobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "cobused")
        // Bolus IOB
        treatments_wizard_bolusiobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "bolusiob"))
        treatments_wizard_bolusiobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "bolusiobused")
        // Basal IOB
        treatments_wizard_basaliobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "basaliob"))
        treatments_wizard_basaliobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "basaliobused")
        // Superbolus
        treatments_wizard_sbinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulinsuperbolus"))
        treatments_wizard_sbcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "superbolusused")
        // Carbs
        treatments_wizard_carbs.text = resourceHelper.gs(R.string.format_carbs_ic, JsonHelper.safeGetDouble(json, "carbs"), JsonHelper.safeGetDouble(json, "ic"))
        treatments_wizard_carbsinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulincarbs"))
        // Correction
        treatments_wizard_correctioninsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "othercorrection"))
        // Profile
        treatments_wizard_profile.text = JsonHelper.safeGetString(json, "profile")
        // Notes
        treatments_wizard_notes.text = JsonHelper.safeGetString(json, "notes")
        // Percentage
        treatments_wizard_percent_used.text = DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "percentageCorrection", 100.0)) + "%"
        // Total
        treatments_wizard_totalinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, JsonHelper.safeGetDouble(json, "insulin"))
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
