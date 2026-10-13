package app.aaps.ui.dialogs

import android.os.Bundle
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.R
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.databinding.DialogProfileviewerBinding
import dagger.android.support.DaggerDialogFragment
import org.json.JSONObject
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileViewerDialog : DaggerDialogFragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var time: Long = 0

    private var mode: UiInteraction.Mode = UiInteraction.Mode.RUNNING_PROFILE
    private var customProfileJson: String = ""
    private var customProfileJson2: String = ""
    private var customProfileName: String = ""

    private var _binding: DialogProfileviewerBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            time = bundle.getLong("time", 0)
            mode = UiInteraction.Mode.entries.toTypedArray()[bundle.getInt("mode", UiInteraction.Mode.RUNNING_PROFILE.ordinal)]
            customProfileJson = bundle.getString("customProfile", "")
            customProfileName = bundle.getString("customProfileName", "")
            if (mode == UiInteraction.Mode.PROFILE_COMPARE)
                customProfileJson2 = bundle.getString("customProfile2", "")
        }

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = DialogProfileviewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.closeLayout.close.setOnClickListener { dismiss() }

        val profile: ProfileSealed?
        val profile2: ProfileSealed?
        val profileName: String?
        val date: String?
        when (mode) {
            UiInteraction.Mode.RUNNING_PROFILE -> {
                val eps = persistenceLayer.getEffectiveProfileSwitchActiveAt(time)
                if (eps == null) {
                    dismiss()
                    return
                }
                profile = ProfileSealed.EPS(eps, activePlugin)
                profile2 = null
                profileName = eps.originalCustomizedName
                date = dateUtil.dateAndTimeString(eps.timestamp)
                binding.dateLayout.visibility = View.VISIBLE
            }

            UiInteraction.Mode.CUSTOM_PROFILE  -> {
                profile = pureProfileFromJson(JSONObject(customProfileJson), dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) }
                profile2 = null
                profileName = customProfileName
                date = ""
                binding.dateLayout.visibility = View.GONE
            }

            UiInteraction.Mode.PROFILE_COMPARE -> {
                profile = pureProfileFromJson(JSONObject(customProfileJson), dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) }
                profile2 = pureProfileFromJson(JSONObject(customProfileJson2), dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) }
                profileName = customProfileName
                binding.headerIcon.setImageResource(R.drawable.ic_compare_profiles)
                date = ""
                binding.dateLayout.visibility = View.GONE
            }

            UiInteraction.Mode.DB_PROFILE      -> {
                val profileList = persistenceLayer.getProfileSwitches()
                profile = if (profileList.isNotEmpty()) ProfileSealed.PS(profileList[0], activePlugin) else null
                profile2 = null
                profileName = if (profileList.isNotEmpty()) profileList[0].getCustomizedName(decimalFormatter) else null
                date = if (profileList.isNotEmpty()) dateUtil.dateAndTimeString(profileList[0].timestamp) else null
                binding.dateLayout.visibility = View.VISIBLE
            }
        }
        binding.noProfile.visibility = View.VISIBLE

        if (mode == UiInteraction.Mode.PROFILE_COMPARE)
            profile?.let { profile1 ->
                profile2?.let { profile2 ->
                    binding.units.text = profileFunction.getUnits().asText
                    binding.dia.text = HtmlHelper.fromHtml(formatColors("", profile1.dia, profile2.dia, DecimalFormat("0.00"), rh.gs(app.aaps.core.interfaces.R.string.shorthour)))
                    val profileNames = profileName!!.split("\n").toTypedArray()
                    binding.activeProfile.text = HtmlHelper.fromHtml(formatColors(profileNames[0], profileNames[1]))
                    binding.date.text = date
                    binding.ic.text = ics(profile1, profile2)
                    binding.isf.text = isfs(profile1, profile2)
                    binding.basal.text = basals(profile1, profile2)
                    binding.target.text = targets(profile1, profile2)
                    binding.basalGraph.show(profile1, profile2)
                    binding.isfGraph.show(profile1, profile2)
                    binding.icGraph.show(profile1, profile2)
                    binding.targetGraph.show(profile1, profile2)
                }

                binding.noProfile.visibility = View.GONE
                val validity = profile1.isValid("ProfileViewDialog", activePlugin.activePump, config, rh, rxBus, hardLimits, false)
                binding.invalidProfile.text = rh.gs(app.aaps.core.ui.R.string.invalid_profile) + "\n" + validity.reasons.joinToString(separator = "\n")
                binding.invalidProfile.visibility = validity.isValid.not().toVisibility()
            }
        else
            profile?.let {
                binding.units.text = it.units.asText
                binding.dia.text = rh.gs(app.aaps.core.ui.R.string.format_hours, it.dia)
                binding.activeProfile.text = profileName
                binding.date.text = date
                binding.ic.text = it.getIcList(rh, dateUtil)
                binding.isf.text = it.getIsfList(rh, dateUtil)
                binding.basal.text = "∑ " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, it.baseBasalSum()) + "\n" + it.getBasalList(rh, dateUtil)
                binding.target.text = it.getTargetList(rh, dateUtil)
                binding.basalGraph.show(it)
                binding.isfGraph.show(it)
                binding.icGraph.show(it)
                binding.targetGraph.show(it)

                binding.noProfile.visibility = View.GONE
                val validity = it.isValid("ProfileViewDialog", activePlugin.activePump, config, rh, rxBus, hardLimits, false)
                binding.invalidProfile.text = rh.gs(app.aaps.core.ui.R.string.invalid_profile) + "\n" + validity.reasons.joinToString(separator = "\n")
                binding.invalidProfile.visibility = validity.isValid.not().toVisibility()
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putLong("time", time)
        bundle.putInt("mode", mode.ordinal)
        bundle.putString("customProfile", customProfileJson)
        bundle.putString("customProfileName", customProfileName)
        if (mode == UiInteraction.Mode.PROFILE_COMPARE)
            bundle.putString("customProfile2", customProfileJson2)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatColors(label: String, value1: Double, value2: Double, format: DecimalFormat, units: String): String {
        return formatColors(label, format.format(value1), format.format(value2), units)
    }

    private fun formatColors(label: String, text1: String, text2: String, units: String): String {
        var s = "<font color='${rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)}'>$label</font>"
        s += "    "
        s += "<font color='${rh.gac(context, app.aaps.core.ui.R.attr.tempBasalColor)}'>$text1</font>"
        s += "    "
        s += "<font color='${rh.gac(context, app.aaps.core.ui.R.attr.examinedProfileColor)}'>$text2</font>"
        s += "    "
        s += "<font color='${rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)}'>$units</font>"
        return s
    }

    private fun formatColors(text1: String, text2: String): String {
        var s = "<font color='${rh.gac(context, app.aaps.core.ui.R.attr.tempBasalColor)}'>$text1</font>"
        s += "<BR/>"
        s += "<font color='${rh.gac(context, app.aaps.core.ui.R.attr.examinedProfileColor)}'>$text2</font>"
        return s
    }

    private fun basals(profile1: Profile, profile2: Profile): Spanned {
        var prev1 = -1.0
        var prev2 = -1.0
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1 = profile1.getBasalTimeFromMidnight(hour * 60 * 60)
            val val2 = profile2.getBasalTimeFromMidnight(hour * 60 * 60)
            if (val1 != prev1 || val2 != prev2) {
                s.append(formatColors(dateUtil.formatHHMM(hour * 60 * 60), val1, val2, DecimalFormat("0.00"), " " + rh.gs(app.aaps.core.ui.R.string.profile_ins_units_per_hour)))
                s.append("<br>")
            }
            prev1 = val1
            prev2 = val2
        }
        s.append(
            formatColors(
                "    ∑ ",
                profile1.baseBasalSum(),
                profile2.baseBasalSum(),
                DecimalFormat("0.00"),
                rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
            )
        )
        return HtmlHelper.fromHtml(s.toString())
    }

    private fun ics(profile1: Profile, profile2: Profile): Spanned {
        var prev1 = -1.0
        var prev2 = -1.0
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1 = profile1.getIcTimeFromMidnight(hour * 60 * 60)
            val val2 = profile2.getIcTimeFromMidnight(hour * 60 * 60)
            if (val1 != prev1 || val2 != prev2) {
                s.append(formatColors(dateUtil.formatHHMM(hour * 60 * 60), val1, val2, DecimalFormat("0.0"), " " + rh.gs(app.aaps.core.ui.R.string.profile_carbs_per_unit)))
                s.append("<br>")
            }
            prev1 = val1
            prev2 = val2
        }
        return HtmlHelper.fromHtml(s.delete(s.length - 4, s.length).toString())
    }

    private fun isfs(profile1: Profile, profile2: Profile): Spanned {
        var prev1 = -1.0
        var prev2 = -1.0
        val units = profileFunction.getUnits()
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1 = profileUtil.fromMgdlToUnits(profile1.getIsfMgdlTimeFromMidnight(hour * 60 * 60))
            val val2 = profileUtil.fromMgdlToUnits(profile2.getIsfMgdlTimeFromMidnight(hour * 60 * 60))
            if (val1 != prev1 || val2 != prev2) {
                s.append(formatColors(dateUtil.formatHHMM(hour * 60 * 60), val1, val2, DecimalFormat("0.0"), units.asText + " " + rh.gs(app.aaps.core.ui.R.string.profile_per_unit)))
                s.append("<br>")
            }
            prev1 = val1
            prev2 = val2
        }
        return HtmlHelper.fromHtml(s.delete(s.length - 4, s.length).toString())
    }

    private fun targets(profile1: Profile, profile2: Profile): Spanned {
        var prev1l = -1.0
        var prev1h = -1.0
        var prev2l = -1.0
        var prev2h = -1.0
        val units = profileFunction.getUnits()
        val s = StringBuilder()
        for (hour in 0..23) {
            val val1l = profile1.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60)
            val val1h = profile1.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60)
            val val2l = profile2.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60)
            val val2h = profile2.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60)
            val txt1 =
                dateUtil.formatHHMM(hour * 60 * 60) + " " + profileUtil.fromMgdlToStringInUnits(val1l) + " - " + profileUtil.fromMgdlToStringInUnits(val1h) + " " + units.asText
            val txt2 =
                dateUtil.formatHHMM(hour * 60 * 60) + " " + profileUtil.fromMgdlToStringInUnits(val2l) + " - " + profileUtil.fromMgdlToStringInUnits(val2h) + " " + units.asText
            if (val1l != prev1l || val1h != prev1h || val2l != prev2l || val2h != prev2h) {
                s.append(formatColors(txt1, txt2))
                s.append("<br>")
            }
            prev1l = val1l
            prev1h = val1h
            prev2l = val2l
            prev2h = val2h
        }
        return HtmlHelper.fromHtml(s.delete(s.length - 4, s.length).toString())
    }
}