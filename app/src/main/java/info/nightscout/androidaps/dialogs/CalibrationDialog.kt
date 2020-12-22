package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.XdripCalibrations
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_calibration.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class CalibrationDialog : DialogFragmentWithDate() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var xdripCalibrations: XdripCalibrations

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_calibration_bg", overview_calibration_bg.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_calibration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val units = profileFunction.getUnits()
        val bg = Profile.fromMgdlToUnits(GlucoseStatus(injector).glucoseStatusData?.glucose
            ?: 0.0, units)
        if (units == Constants.MMOL)
            overview_calibration_bg.setParams(savedInstanceState?.getDouble("overview_calibration_bg")
                ?: bg, 2.0, 30.0, 0.1, DecimalFormat("0.0"), false, ok)
        else
            overview_calibration_bg.setParams(savedInstanceState?.getDouble("overview_calibration_bg")
                ?: bg, 36.0, 500.0, 1.0, DecimalFormat("0"), false, ok)
        overview_calibration_units.text = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)
    }

    override fun submit(): Boolean {
        val units = profileFunction.getUnits()
        val unitLabel = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)
        val actions: LinkedList<String?> = LinkedList()
        val bg = overview_calibration_bg?.value ?: return false
        actions.add(resourceHelper.gs(R.string.treatments_wizard_bg_label) + ": " + Profile.toCurrentUnitsString(profileFunction, bg) + " " + unitLabel)
        if (bg > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.overview_calibration), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    aapsLogger.debug("USER ENTRY: CALIBRATION $bg")
                    xdripCalibrations.sendIntent(bg)
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.overview_calibration), resourceHelper.gs(R.string.no_action_selected))
            }
        return true
    }
}
