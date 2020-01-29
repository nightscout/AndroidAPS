package info.nightscout.androidaps.plugins.treatments.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.StringUtils
import kotlinx.android.synthetic.main.treatments_wizardinfo_dialog.*
import org.json.JSONObject

class WizardInfoDialog : DialogFragment() {
    private var json: JSONObject? = null

    fun setData(json: JSONObject) {
        this.json = json
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        return inflater.inflate(R.layout.treatments_wizardinfo_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.setOnClickListener { dismiss() }
        val units = ProfileFunctions.getSystemUnits()
        val bg_string: String
        if (units.equals(Constants.MGDL)) { bg_string = DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "bg"))} else { bg_string = DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "bg"))}
        // BG
        treatments_wizard_bg.text = MainApp.gs(R.string.format_bg_isf, bg_string , JsonHelper.safeGetDouble(json, "isf"))
        treatments_wizard_bginsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "insulinbg"))
        treatments_wizard_bgcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "insulinbgused")
        treatments_wizard_ttcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "ttused")
        // Trend
        treatments_wizard_bgtrend.text = JsonHelper.safeGetString(json, "trend")
        treatments_wizard_bgtrendinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "insulintrend"))
        treatments_wizard_bgtrendcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "trendused")
        // COB
        treatments_wizard_cob.text = MainApp.gs(R.string.format_cob_ic, JsonHelper.safeGetDouble(json, "cob"), JsonHelper.safeGetDouble(json, "ic"))
        treatments_wizard_cobinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "insulincob"))
        treatments_wizard_cobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "cobused")
        // Bolus IOB
        treatments_wizard_bolusiobinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "bolusiob"))
        treatments_wizard_bolusiobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "bolusiobused")
        // Basal IOB
        treatments_wizard_basaliobinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "basaliob"))
        treatments_wizard_basaliobcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "basaliobused")
        // Superbolus
        treatments_wizard_sbinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "insulinsuperbolus"))
        treatments_wizard_sbcheckbox.isChecked = JsonHelper.safeGetBoolean(json, "superbolusused")
        // Carbs
        treatments_wizard_carbs.text = MainApp.gs(R.string.format_carbs_ic, JsonHelper.safeGetDouble(json, "carbs"), JsonHelper.safeGetDouble(json, "ic"))
        treatments_wizard_carbsinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "insulincarbs"))
        // Correction
        treatments_wizard_correctioninsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "othercorrection"))
        // Profile
        treatments_wizard_profile.text = JsonHelper.safeGetString(json, "profile")
        // Notes
        treatments_wizard_notes.text = JsonHelper.safeGetString(json, "notes")
        // Percentage
        treatments_wizard_percent_used.text = DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "percentageCorrection", 100.0)) + "%"
        // Total
        treatments_wizard_totalinsulin.text = StringUtils.formatInsulin(JsonHelper.safeGetDouble(json, "insulin"))
    }
}
