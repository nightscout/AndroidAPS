package info.nightscout.androidaps.plugins.general.autotune

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.AutotuneFragmentBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile
import info.nightscout.androidaps.plugins.general.autotune.events.EventAutotuneUpdateGui
import info.nightscout.androidaps.plugins.general.autotune.events.EventAutotuneUpdateResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.interfaces.Autotune
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.SafeParse
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class AutotuneFragment : DaggerFragment() {
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var autotunePlugin: Autotune
    @Inject lateinit var sp: SP
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var lastRun: Long = 0
    private var lastRunTxt: String? = null
    private val log = LoggerFactory.getLogger(AutotunePlugin::class.java)
    private var _binding: AutotuneFragmentBinding? = null
    private lateinit var profileStore: ProfileStore
    private var profileName = ""
    private lateinit var profile: ATProfile
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutotuneFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val defaultValue = sp.getInt(R.string.key_autotune_default_tune_days, 5).toDouble()
        profileStore = activePlugin.activeProfileSource.profile ?: ProfileStore(injector, JSONObject(), dateUtil)
        profileName = if (binding.profileList.text.toString() == rh.gs(R.string.active)) "" else binding.profileList.text.toString()
        profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?:profileFunction.getProfile(), LocalInsulin(""), injector)

        binding.tuneDays.setParams(
            savedInstanceState?.getDouble("tunedays")
                ?: defaultValue, 1.0, 30.0, 1.0, DecimalFormat("0"), false, null, textWatcher)
        binding.autotuneRun.setOnClickListener {
            val daysBack = SafeParse.stringToInt(binding.tuneDays.text)
            autotunePlugin.calculationRunning = true
            autotunePlugin.lastNbDays = daysBack.toString()
            autotunePlugin.copyButtonVisibility = View.GONE
            autotunePlugin.profileSwitchButtonVisibility = View.GONE
            Thread(Runnable {
                autotunePlugin.aapsAutotune(daysBack, false, profileName)
            }).start()
            lastRunTxt = dateUtil.dateAndTimeString(autotunePlugin.lastRun)
            lastRun = autotunePlugin.lastRun
            updateGui()
        }
        binding.profileList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
            profileName = if (binding.profileList.text.toString() == rh.gs(R.string.active)) "" else binding.profileList.text.toString()
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?:profileFunction.getProfile(), LocalInsulin(""), injector)
            resetParam()
            updateGui()
        }

        binding.autotuneCopylocal.setOnClickListener {
            val localName = resourceHelper.gs(R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(autotunePlugin.lastRun)
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            autotunePlugin.tunedProfile?.let {  tunedProfile ->
                showConfirmation(requireContext(),
                                 resourceHelper.gs(R.string.autotune_copy_localprofile_button),
                                 resourceHelper.gs(R.string.autotune_copy_local_profile_message) + "\n" + localName + " " + dateUtil.dateAndTimeString(lastRun),
                                 Runnable {
                                     localProfilePlugin.addProfile(localProfilePlugin.copyFrom(tunedProfile.getProfile(circadian), localName))
                                     rxBus.send(EventLocalProfileChanged())
                                     autotunePlugin.copyButtonVisibility = View.GONE
                                     updateGui()
                                 })
            }
        }

        binding.autotuneCompare.setOnClickListener {
            val pumpProfile = autotunePlugin.pumpProfile
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            val tunedprofile = if (circadian) autotunePlugin.tunedProfile?.circadianProfile else autotunePlugin.tunedProfile?.profile
            ProfileViewerDialog().also { pvd ->
                pvd.arguments = Bundle().also {
                    it.putLong("time", dateUtil.now())
                    it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                    it.putString("customProfile", pumpProfile?.profile?.toPureNsJson(dateUtil).toString())
                    it.putString("customProfile2", tunedprofile?.toPureNsJson(dateUtil).toString())
                    it.putString("customProfileUnits", profileFunction.getUnits().asText)
                    it.putString("customProfileName", pumpProfile?.profilename + "\n" + autotunePlugin.tunedProfile?.profilename)
                }
            }.show(childFragmentManager, "ProfileViewDialog")
        }

        binding.autotuneProfileswitch.setOnClickListener{
            val tunedProfile = autotunePlugin.tunedProfile
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            tunedProfile?.let { tunedP ->
                log("ProfileSwitch pressed")
                tunedP.profileStore(circadian)?.let {
                    showConfirmation(requireContext(), resourceHelper.gs(R.string.activate_profile) + ": " + tunedP.profilename + " ?", Runnable {
                        val now = dateUtil.now()
                        if (profileFunction.createProfileSwitch(
                                it,
                                profileName = tunedP.profilename,
                                durationInMinutes = 0,
                                percentage = 100,
                                timeShiftInHours = 0,
                                timestamp = now
                            )
                        ) {
                            uel.log(
                                UserEntry.Action.PROFILE_SWITCH,
                                UserEntry.Sources.Autotune,
                                "Autotune AutoSwitch",
                                ValueWithUnit.SimpleString(autotunePlugin.tunedProfile!!.profilename))
                        }
                        rxBus.send(EventLocalProfileChanged())
                        updateGui()
                    })
                }
                    ?: log("ProfileStore is null!")
            }

        }

        lastRun = autotunePlugin.lastRun
        if (lastRun > MidnightTime.calc(System.currentTimeMillis() - autotunePlugin.autotuneStartHour * 3600 * 1000L) + autotunePlugin.autotuneStartHour * 3600 * 1000L && autotunePlugin.result !=="")
        {
            binding.tuneWarning.text = resourceHelper.gs(R.string.autotune_warning_after_run)
            binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
        } else { //if new day reinit result, default days, warning and button's visibility
            resetParam()
        }
        updateGui()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        profileStore = activePlugin.activeProfileSource.profile ?: ProfileStore(injector, JSONObject(), dateUtil)
        profileName = if (binding.profileList.text.toString() == rh.gs(R.string.active)) "" else binding.profileList.text.toString()
        profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?:profileFunction.getProfile(), LocalInsulin(""), injector)
        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        profileList.add(0, rh.gs(R.string.active))
        context?.let { context ->
            binding.profileList.setAdapter(ArrayAdapter(context, R.layout.spinner_centered, profileList))
            binding.profileList.setText(if (!autotunePlugin.selectedProfile.isEmpty()) profileList[0] else autotunePlugin.selectedProfile, false)
        }
        disposable += rxBus
            .toObservable(EventAutotuneUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateGui()
            }, { fabricPrivacy.logException(it) })

        disposable += rxBus
            .toObservable(EventAutotuneUpdateResult::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateGui()
            }, { fabricPrivacy.logException(it) })

        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGui() {
        if (autotunePlugin.calculationRunning) {
            binding.autotuneRun.visibility = View.GONE
            binding.autotuneCompare.visibility = View.GONE
            binding.tuneWarning.text = resourceHelper.gs(R.string.autotune_warning_during_run)
            binding.tuneResult.text = autotunePlugin.result
        } else if (autotunePlugin.lastRunSuccess) {
            binding.autotuneRun.visibility = View.VISIBLE
            binding.tuneWarning.text = resourceHelper.gs(R.string.autotune_warning_after_run)
            binding.tuneResult.text = autotunePlugin.result
            binding.autotuneCompare.visibility = View.VISIBLE
        } else {
            binding.tuneResult.text = autotunePlugin.result
            binding.autotuneRun.visibility = View.VISIBLE
        }
        if (autotunePlugin.tunedProfile == null || autotunePlugin.pumpProfile == null)
            binding.autotuneCompare.visibility = View.GONE
        binding.autotuneCopylocal.visibility = autotunePlugin.copyButtonVisibility
        binding.autotuneProfileswitch.visibility = autotunePlugin.profileSwitchButtonVisibility
        lastRunTxt = if (autotunePlugin.lastRun != 0L) dateUtil.dateAndTimeString(autotunePlugin.lastRun) else ""
        binding.tuneLastrun.text = lastRunTxt
    }

    private fun addWarnings(): String {
        var warning = ""
        var nl = ""
        if (!profile.isValid) return resourceHelper.gs(R.string.autotune_profile_invalid)
        if (profile.icSize > 1) {
            warning += nl + resourceHelper.gs(R.string.format_autotune_ic_warning, profile.icSize, profile.ic)
            nl = "\n"
        }
        if (profile.isfSize > 1) {
            warning += nl + resourceHelper.gs(R.string.format_autotune_isf_warning, profile.isfSize, Profile.fromMgdlToUnits(profile.isf, profileFunction.getUnits()), profileFunction.getUnits().asText)
        }
        return warning
    }

    private fun resetParam(resetDay: Boolean = true) {
        binding.tuneWarning.text = addWarnings()
        if (resetDay)
            binding.tuneDays.value = sp.getInt(R.string.key_autotune_default_tune_days, 5).toDouble()
        autotunePlugin.result = ""
        autotunePlugin.tunedProfile = null
        autotunePlugin.lastRun = 0
        autotunePlugin.lastRunSuccess = false
        autotunePlugin.profileSwitchButtonVisibility = View.GONE
        autotunePlugin.copyButtonVisibility = View.GONE
        binding.autotuneCompare.visibility = View.GONE
    }

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) { updateGui() }
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (!binding.tuneDays.text.isEmpty()) {
                try {
                    if (autotunePlugin.calculationRunning)
                        binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
                    if (binding.tuneDays.value != autotunePlugin.lastNbDays.toDouble())
                        resetParam(false)
                } catch (e:Exception) { }
            }
        }
    }

    private fun log(message: String) {
        autotunePlugin.atLog("[Fragment] $message")
    }
}