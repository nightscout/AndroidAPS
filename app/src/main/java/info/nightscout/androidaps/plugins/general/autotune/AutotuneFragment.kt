package info.nightscout.androidaps.plugins.general.autotune

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import info.nightscout.androidaps.activities.TreatmentsActivity
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.interfaces.Autotune
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.slf4j.LoggerFactory
import java.util.*
import javax.inject.Inject

/**
 * initialised by Rumen Georgiev on 1/29/2018.
 * Deep rework by philoul on 06/2020
 */
// Todo: Reset results field and Switch/Copy button visibility when Nb of selected days is changed
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
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var lastRun: Long = 0
    private var lastRunTxt: String? = null
    private var tempResult = ""
    private val log = LoggerFactory.getLogger(AutotunePlugin::class.java)
    private var _binding: AutotuneFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutotuneFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.autotuneRun.setOnClickListener {
            val daysBack = binding.tuneDays.text.toString().toInt()
            if (daysBack > 0) {
                tempResult = ""
                autotunePlugin.calculationRunning = true
                autotunePlugin.copyButtonVisibility = View.GONE
                autotunePlugin.profileSwitchButtonVisibility = View.GONE
                Thread(Runnable {
                    autotunePlugin.aapsAutotune(daysBack, false)
                }).start()
                lastRunTxt = dateUtil.dateAndTimeString(autotunePlugin.lastRun)
                lastRun = autotunePlugin.lastRun
                updateGui()
            } else binding.tuneResult.text = resourceHelper.gs(R.string.autotune_min_days)
        }

        binding.autotuneCopylocal.setOnClickListener {
            val localName = resourceHelper.gs(R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(autotunePlugin.lastRun)
            showConfirmation(requireContext(), resourceHelper.gs(R.string.autotune_copy_localprofile_button), resourceHelper.gs(R.string.autotune_copy_local_profile_message) + "\n" + localName + " " + dateUtil.dateAndTimeString(lastRun),
                Runnable {
                    localProfilePlugin.addProfile(localProfilePlugin.copyFrom(autotunePlugin.tunedProfile!!.getProfile(), localName))
                    rxBus.send(EventLocalProfileChanged())
                    autotunePlugin.copyButtonVisibility = View.GONE
                    updateGui()
                })
        }

        binding.autotuneCompare.setOnClickListener {
            val currentprofile = autotunePlugin.currentprofile
            val tunedprofile = autotunePlugin.tunedProfile
            ProfileViewerDialog().also { pvd ->
                pvd.arguments = Bundle().also {
                    it.putLong("time", dateUtil.now())
                    it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                    it.putString("customProfile", currentprofile?.profile?.toPureNsJson(dateUtil).toString())
                    it.putString("customProfile2", tunedprofile?.profile?.toPureNsJson(dateUtil).toString())
                    it.putString("customProfileUnits", profileFunction.getUnits().asText)
                    it.putString("customProfileName", currentprofile?.profilename + "\n" + autotunePlugin.tunedProfile?.profilename)
                }
            }.show(childFragmentManager, "ProfileViewDialog")
        }

        binding.autotuneProfileswitch.setOnClickListener{
            val tunedProfile = autotunePlugin.tunedProfile
            tunedProfile?.let { tunedP ->
                val profileStore = tunedP.profileStore
                log("ProfileSwitch pressed")
                if (profileStore != null) {
                    showConfirmation(requireContext(), resourceHelper.gs(R.string.activate_profile) + ": " + tunedP.profilename + " ?", Runnable {
                        val now = dateUtil.now()
                        if (profileFunction.createProfileSwitch(
                                profileStore,
                                profileName = tunedP.profilename!!,
                                durationInMinutes = 0,
                                percentage = 100,
                                timeShiftInHours = 0,
                                timestamp = now
                            )
                        ) {
                            uel.log(
                                UserEntry.Action.PROFILE_SWITCH,
                                UserEntry.Sources.ProfileSwitchDialog,
                                "Autotune AutoSwitch",
                                ValueWithUnit.SimpleString(autotunePlugin.tunedProfile!!.profilename!!))
                        }
                        rxBus.send(EventLocalProfileChanged())
                        autotunePlugin.profileSwitchButtonVisibility = View.GONE
                        updateGui()
                    })
                } else log("ProfileStore is null!")

            }

        }

        lastRun = autotunePlugin.lastRun
        if (lastRun > MidnightTime.calc(System.currentTimeMillis() - autotunePlugin.autotuneStartHour * 3600 * 1000L) + autotunePlugin.autotuneStartHour * 3600 * 1000L && autotunePlugin.result !=="")
        {
            binding.tuneWarning.text = resourceHelper.gs(R.string.autotune_warning_after_run)
            binding.tuneDays.setText(autotunePlugin.lastNbDays)
        } else { //if new day reinit result, default days, warning and button's visibility
            binding.tuneWarning.text = addWarnings()
            binding.tuneDays.setText(sp.getString(R.string.key_autotune_default_tune_days, "5"))
            autotunePlugin.result = ""
            autotunePlugin.tunedProfile = null
            autotunePlugin.profileSwitchButtonVisibility = View.GONE
            autotunePlugin.copyButtonVisibility = View.GONE
            binding.autotuneCompare.visibility = View.GONE
        }
        updateGui()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
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
                tempResult = it.result
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
            binding.tuneResult.text = tempResult
        } else if (autotunePlugin.lastRunSuccess) {
            binding.autotuneRun.visibility = View.VISIBLE
            binding.tuneWarning.text = resourceHelper.gs(R.string.autotune_warning_after_run)
            binding.tuneResult.text = autotunePlugin.result
            binding.autotuneCompare.visibility = View.VISIBLE
        } else {
            binding.tuneResult.text = tempResult
            binding.autotuneRun.visibility = View.VISIBLE
        }
        if (autotunePlugin.tunedProfile == null || autotunePlugin.currentprofile == null)
            binding.autotuneCompare.visibility = View.GONE
        binding.autotuneCopylocal.visibility = autotunePlugin.copyButtonVisibility
        binding.autotuneProfileswitch.visibility = autotunePlugin.profileSwitchButtonVisibility
        lastRunTxt = if (autotunePlugin.lastRun != 0L) dateUtil.dateAndTimeString(autotunePlugin.lastRun) else ""
        binding.tuneLastrun.text = lastRunTxt
    }

    private fun addWarnings(): String {
        var warning = resourceHelper.gs(R.string.autotune_warning_before_run)
        var nl = "\n"
        val profile = ATProfile(profileFunction.getProfile(System.currentTimeMillis()), LocalInsulin(""), injector)
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

    private fun log(message: String) {
        autotunePlugin.atLog("[Fragment] $message")
    }
}