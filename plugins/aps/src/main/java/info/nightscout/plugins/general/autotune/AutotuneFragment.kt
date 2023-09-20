package info.nightscout.plugins.general.autotune

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.elements.WeekDay
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.databinding.AutotuneFragmentBinding
import info.nightscout.plugins.general.autotune.data.ATProfile
import info.nightscout.plugins.general.autotune.data.LocalInsulin
import info.nightscout.plugins.general.autotune.events.EventAutotuneUpdateGui
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventLocalProfileChanged
import info.nightscout.shared.SafeParse
import info.nightscout.shared.extensions.runOnUiThread
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import java.text.DecimalFormat
import javax.inject.Inject

class AutotuneFragment : DaggerFragment() {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var autotuneFS: AutotuneFS
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var instantiator: Instantiator

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var _binding: AutotuneFragmentBinding? = null
    private lateinit var profileStore: ProfileStore
    private var profileName = ""
    private var profile: ATProfile? = null
    private val days get() = autotunePlugin.days
    private val daysBack get() = SafeParse.stringToInt(binding.tuneDays.text)
    private val calcDays get() = autotunePlugin.calcDays(daysBack)

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutotuneFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sp.putBoolean(info.nightscout.core.utils.R.string.key_autotune_tune_insulin_curve, false)  // put to false tune insulin curve
        sp.putBoolean(info.nightscout.core.utils.R.string.key_autotune_additional_log, false)      // put to false additional log
        autotunePlugin.loadLastRun()
        if (autotunePlugin.lastNbDays.isEmpty())
            autotunePlugin.lastNbDays = sp.getInt(info.nightscout.core.utils.R.string.key_autotune_default_tune_days, 5).toString()
        val defaultValue = sp.getInt(info.nightscout.core.utils.R.string.key_autotune_default_tune_days, 5).toDouble()
        profileStore = activePlugin.activeProfileSource.profile ?: instantiator.provideProfileStore(JSONObject())
        profileName = if (binding.profileList.text.toString() == rh.gs(info.nightscout.core.ui.R.string.active)) "" else binding.profileList.text.toString()
        profileFunction.getProfile()?.let { currentProfile ->
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector)
        }
        days.addToLayout(binding.selectWeekDays)
        days.view?.setOnWeekdaysChangeListener { i: Int, selected: Boolean ->
            if (autotunePlugin.calculationRunning)
                days.view?.setSelectedDays(days.getSelectedDays())
            else {
                days.set(WeekDay.DayOfWeek.fromCalendarInt(i), selected)
                resetParam(false)
                updateGui()
            }
        }

        binding.tuneDays.setParams(
            savedInstanceState?.getDouble("tunedays")
                ?: defaultValue, 1.0, 30.0, 1.0, DecimalFormat("0"), false, null, textWatcher
        )
        binding.autotuneRun.setOnClickListener {
            autotunePlugin.lastNbDays = daysBack.toString()
            log("Run Autotune $profileName, $daysBack days")
            handler.post { autotunePlugin.aapsAutotune(daysBack, false, profileName) }
            updateGui()
        }

        binding.showWeekDaysCheckbox.setOnCheckedChangeListener { _, isChecked ->
            run {
                binding.selectWeekDays.visibility = isChecked.toVisibility()
            }
        }

        binding.profileList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
            if (!autotunePlugin.calculationRunning) {
                profileName = if (binding.profileList.text.toString() == rh.gs(info.nightscout.core.ui.R.string.active)) "" else binding.profileList.text.toString()
                profileFunction.getProfile()?.let { currentProfile ->
                    profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector)
                }
                autotunePlugin.selectedProfile = profileName
                resetParam(true)
                binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
            }
            updateGui()
        }

        binding.autotuneCopyLocal.setOnClickListener {
            val localName = rh.gs(info.nightscout.core.ui.R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(autotunePlugin.lastRun)
            val circadian = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_circadian_ic_isf, false)
            autotunePlugin.tunedProfile?.let { tunedProfile ->
                OKDialog.showConfirmation(requireContext(),
                                          rh.gs(info.nightscout.core.ui.R.string.autotune_copy_localprofile_button),
                                          rh.gs(info.nightscout.core.ui.R.string.autotune_copy_local_profile_message) + "\n" + localName,
                                          Runnable {
                                     val profilePlugin = activePlugin.activeProfileSource
                                     profilePlugin.addProfile(profilePlugin.copyFrom(tunedProfile.getProfile(circadian), localName))
                                     rxBus.send(EventLocalProfileChanged())
                                     uel.log(
                                         UserEntry.Action.NEW_PROFILE,
                                         UserEntry.Sources.Autotune,
                                         ValueWithUnit.SimpleString(localName)
                                     )
                                     updateGui()
                                 })
            }
        }

        binding.autotuneUpdateProfile.setOnClickListener {
            val localName = autotunePlugin.pumpProfile.profileName
            OKDialog.showConfirmation(requireContext(),
                                      rh.gs(info.nightscout.core.ui.R.string.autotune_update_input_profile_button),
                                      rh.gs(info.nightscout.core.ui.R.string.autotune_update_local_profile_message, localName),
                                      Runnable {
                                 autotunePlugin.tunedProfile?.profileName = localName
                                 autotunePlugin.updateProfile(autotunePlugin.tunedProfile)
                                 autotunePlugin.updateButtonVisibility = View.GONE
                                 autotunePlugin.saveLastRun()
                                 uel.log(
                                     UserEntry.Action.STORE_PROFILE,
                                     UserEntry.Sources.Autotune,
                                     ValueWithUnit.SimpleString(localName)
                                 )
                                 updateGui()
                             }
            )
        }

        binding.autotuneRevertProfile.setOnClickListener {
            val localName = autotunePlugin.pumpProfile.profileName
            OKDialog.showConfirmation(requireContext(),
                                      rh.gs(info.nightscout.core.ui.R.string.autotune_revert_input_profile_button),
                                      rh.gs(info.nightscout.core.ui.R.string.autotune_revert_local_profile_message, localName),
                                      Runnable {
                                 autotunePlugin.tunedProfile?.profileName = ""
                                 autotunePlugin.updateProfile(autotunePlugin.pumpProfile)
                                 autotunePlugin.updateButtonVisibility = View.VISIBLE
                                 autotunePlugin.saveLastRun()
                                 uel.log(
                                     UserEntry.Action.STORE_PROFILE,
                                     UserEntry.Sources.Autotune,
                                     ValueWithUnit.SimpleString(localName)
                                 )
                                 updateGui()
                             }
            )
        }

        binding.autotuneCheckInputProfile.setOnClickListener {
            val pumpProfile = profileFunction.getProfile()?.let { currentProfile ->
                profileStore.getSpecificProfile(profileName)?.let { specificProfile ->
                    ATProfile(ProfileSealed.Pure(specificProfile), LocalInsulin(""), injector).also {
                        it.profileName = profileName
                    }
                }
                    ?: ATProfile(currentProfile, LocalInsulin(""), injector).also {
                        it.profileName = profileFunction.getProfileName()
                    }
            }
            pumpProfile?.let {
                uiInteraction.runProfileViewerDialog(
                    fragmentManager = childFragmentManager,
                    time = dateUtil.now(),
                    mode = UiInteraction.Mode.CUSTOM_PROFILE,
                    customProfile = pumpProfile.profile.toPureNsJson(dateUtil).toString(),
                    customProfileName = pumpProfile.profileName
                )
            }
        }

        binding.autotuneCompare.setOnClickListener {
            val pumpProfile = autotunePlugin.pumpProfile
            val circadian = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_circadian_ic_isf, false)
            val tunedProfile = if (circadian) autotunePlugin.tunedProfile?.circadianProfile else autotunePlugin.tunedProfile?.profile
            uiInteraction.runProfileViewerDialog(
                fragmentManager = childFragmentManager,
                time = dateUtil.now(),
                mode = UiInteraction.Mode.PROFILE_COMPARE,
                customProfile = pumpProfile.profile.toPureNsJson(dateUtil).toString(),
                customProfileName = pumpProfile.profileName + "\n" + rh.gs(info.nightscout.core.ui.R.string.autotune_tunedprofile_name),
                customProfile2 = tunedProfile?.toPureNsJson(dateUtil).toString()
            )
        }

        binding.autotuneProfileswitch.setOnClickListener {
            val tunedProfile = autotunePlugin.tunedProfile
            autotunePlugin.updateProfile(tunedProfile)
            val circadian = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_circadian_ic_isf, false)
            tunedProfile?.let { tunedP ->
                tunedP.profileStore(circadian)?.let {
                    OKDialog.showConfirmation(requireContext(),
                                              rh.gs(info.nightscout.core.ui.R.string.activate_profile) + ": " + tunedP.profileName + " ?",
                                              {
                                                  uel.log(
                                                      UserEntry.Action.STORE_PROFILE,
                                                      UserEntry.Sources.Autotune,
                                                      ValueWithUnit.SimpleString(tunedP.profileName)
                                                  )
                                                  val now = dateUtil.now()
                                                  if (profileFunction.createProfileSwitch(
                                                          it,
                                                          profileName = tunedP.profileName,
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
                                                          ValueWithUnit.SimpleString(autotunePlugin.tunedProfile!!.profileName)
                                                      )
                                                  }
                                                  rxBus.send(EventLocalProfileChanged())
                                                  updateGui()
                                              }
                    )
                }
            }
        }

        binding.tuneLastRun.setOnClickListener {
            if (!autotunePlugin.calculationRunning) {
                autotunePlugin.loadLastRun()
                binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
                updateGui()
            }
        }
        binding.tuneLastRun.paintFlags = binding.tuneLastRun.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventAutotuneUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        checkNewDay()
        binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
        binding.selectWeekDays.visibility = binding.showWeekDaysCheckbox.isChecked.toVisibility()
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    private fun updateGui() {
        _binding ?: return
        profileStore = activePlugin.activeProfileSource.profile ?: instantiator.provideProfileStore(JSONObject())
        profileName = if (binding.profileList.text.toString() == rh.gs(info.nightscout.core.ui.R.string.active)) "" else binding.profileList.text.toString()
        profileFunction.getProfile()?.let { currentProfile ->
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector)
        }
        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        profileList.add(0, rh.gs(info.nightscout.core.ui.R.string.active))
        context?.let { context ->
            binding.profileList.setAdapter(ArrayAdapter(context, info.nightscout.core.ui.R.layout.spinner_centered, profileList))
        } ?: return
        // set selected to actual profile
        if (autotunePlugin.selectedProfile.isNotEmpty())
            binding.profileList.setText(autotunePlugin.selectedProfile, false)
        else {
            binding.profileList.setText(profileList[0], false)
        }
        days.view?.setSelectedDays(days.getSelectedDays())
        binding.autotuneRun.visibility = View.GONE
        binding.autotuneCheckInputProfile.visibility = View.GONE
        binding.autotuneCopyLocal.visibility = View.GONE
        binding.autotuneUpdateProfile.visibility = View.GONE
        binding.autotuneRevertProfile.visibility = View.GONE
        binding.autotuneProfileswitch.visibility = View.GONE
        binding.autotuneCompare.visibility = View.GONE
        when {
            autotunePlugin.calculationRunning -> {
                binding.tuneWarning.text = rh.gs(info.nightscout.core.ui.R.string.autotune_warning_during_run)
            }

            autotunePlugin.lastRunSuccess     -> {
                binding.autotuneCopyLocal.visibility = View.VISIBLE
                binding.autotuneUpdateProfile.visibility = autotunePlugin.updateButtonVisibility
                binding.autotuneRevertProfile.visibility = if (autotunePlugin.updateButtonVisibility == View.VISIBLE) View.GONE else View.VISIBLE
                binding.autotuneProfileswitch.visibility = View.VISIBLE
                binding.tuneWarning.text = rh.gs(info.nightscout.core.ui.R.string.autotune_warning_after_run)
                binding.autotuneCompare.visibility = View.VISIBLE
            }

            else                              -> {
                if (profile?.isValid == true && calcDays > 0)
                    binding.autotuneRun.visibility = View.VISIBLE
                binding.autotuneCheckInputProfile.visibility = View.VISIBLE
            }
        }
        binding.calcDays.text = calcDays.toString()
        binding.calcDays.visibility = if (daysBack == calcDays) View.INVISIBLE else View.VISIBLE
        binding.tuneLastRun.text = dateUtil.dateAndTimeString(autotunePlugin.lastRun)
        showResults()
    }

    private fun checkNewDay() {
        val runToday = autotunePlugin.lastRun > MidnightTime.calc(dateUtil.now() - autotunePlugin.autotuneStartHour * 3600 * 1000L) + autotunePlugin.autotuneStartHour * 3600 * 1000L
        if (runToday && autotunePlugin.result != "") {
            binding.tuneWarning.text = rh.gs(info.nightscout.core.ui.R.string.autotune_warning_after_run)
        } else if (!runToday || autotunePlugin.result.isEmpty()) //if new day re-init result, default days, warning and button's visibility
            resetParam(!runToday)
    }

    private fun addWarnings(): String {
        var warning = ""
        var nl = ""
        if (profileFunction.getProfile() == null) {
            warning = rh.gs(info.nightscout.core.ui.R.string.profileswitch_ismissing)
            return warning
        }
        profileFunction.getProfile()?.let { currentProfile ->
            profile = ATProfile(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it) } ?: currentProfile, LocalInsulin(""), injector).also { profile ->
                if (!profile.isValid) return rh.gs(info.nightscout.core.ui.R.string.autotune_profile_invalid)
                if (profile.icSize > 1) {
                    warning += nl + rh.gs(info.nightscout.core.ui.R.string.autotune_ic_warning, profile.icSize, profile.ic)
                    nl = "\n"
                }
                if (profile.isfSize > 1) {
                    warning += nl + rh.gs(info.nightscout.core.ui.R.string.autotune_isf_warning, profile.isfSize, profileUtil.fromMgdlToUnits(profile.isf), profileFunction.getUnits().asText)
                }
            }
        }
        return warning
    }

    private fun resetParam(resetDay: Boolean) {
        binding.tuneWarning.text = addWarnings()
        if (resetDay) {
            autotunePlugin.lastNbDays = sp.getInt(info.nightscout.core.utils.R.string.key_autotune_default_tune_days, 5).toString()
            days.setAll(true)
        }
        autotunePlugin.result = ""
        binding.autotuneResults.removeAllViews()
        autotunePlugin.tunedProfile = null
        autotunePlugin.lastRunSuccess = false
        autotunePlugin.updateButtonVisibility = View.GONE
    }

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            _binding?.let { updateGui() }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (binding.tuneDays.text.isNotEmpty()) {
                try {
                    if (autotunePlugin.calculationRunning)
                        binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
                    if (binding.tuneDays.text != autotunePlugin.lastNbDays) {
                        autotunePlugin.lastNbDays = binding.tuneDays.text
                        resetParam(false)
                    }
                } catch (e: Exception) {
                    fabricPrivacy.logException(e)
                }
            }
        }
    }

    private fun showResults() {
        context?.let { context ->
            runOnUiThread {
                _binding?.let {
                    binding.autotuneResults.removeAllViews()
                    if (autotunePlugin.result.isNotBlank()) {
                        var toMgDl = 1.0
                        if (profileFunction.getUnits() == GlucoseUnit.MMOL) toMgDl = Constants.MMOLL_TO_MGDL
                        val isfFormat = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "%.2f" else "%.1f"
                        binding.autotuneResults.addView(
                            TableLayout(context).also { layout ->
                                layout.addView(
                                    TextView(context).apply {
                                        text = autotunePlugin.result
                                        setTypeface(typeface, Typeface.BOLD)
                                        gravity = Gravity.CENTER_HORIZONTAL
                                        setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                                    })
                                autotunePlugin.tunedProfile?.let { tuned ->
                                    layout.addView(toTableRowHeader(context))
                                    val tuneInsulin = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_tune_insulin_curve, false)
                                    if (tuneInsulin) {
                                        layout.addView(
                                            toTableRowValue(
                                                context,
                                                rh.gs(R.string.insulin_peak),
                                                autotunePlugin.pumpProfile.localInsulin.peak.toDouble(),
                                                tuned.localInsulin.peak.toDouble(),
                                                "%.0f"
                                            )
                                        )
                                        layout.addView(
                                            toTableRowValue(
                                                context,
                                                rh.gs(info.nightscout.core.ui.R.string.dia),
                                                Round.roundTo(autotunePlugin.pumpProfile.localInsulin.dia, 0.1),
                                                Round.roundTo(tuned.localInsulin.dia, 0.1),
                                                "%.1f"
                                            )
                                        )
                                    }
                                    layout.addView(
                                        toTableRowValue(
                                            context,
                                            rh.gs(info.nightscout.core.ui.R.string.isf_short),
                                            Round.roundTo(autotunePlugin.pumpProfile.isf / toMgDl, 0.001),
                                            Round.roundTo(tuned.isf / toMgDl, 0.001),
                                            isfFormat
                                        )
                                    )
                                    layout.addView(toTableRowValue(context, rh.gs(info.nightscout.core.ui.R.string.ic_short), Round.roundTo(autotunePlugin.pumpProfile.ic, 0.001), Round.roundTo(tuned.ic, 0.001), "%.2f"))
                                    layout.addView(
                                        TextView(context).apply {
                                            text = rh.gs(info.nightscout.core.ui.R.string.basal)
                                            setTypeface(typeface, Typeface.BOLD)
                                            gravity = Gravity.CENTER_HORIZONTAL
                                            setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                                        }
                                    )
                                    layout.addView(toTableRowHeader(context, true))
                                    var totalPump = 0.0
                                    var totalTuned = 0.0
                                    for (h in 0 until tuned.basal.size) {
                                        val df = DecimalFormat("00")
                                        val time = df.format(h.toLong()) + ":00"
                                        totalPump += autotunePlugin.pumpProfile.basal[h]
                                        totalTuned += tuned.basal[h]
                                        layout.addView(toTableRowValue(context, time, autotunePlugin.pumpProfile.basal[h], tuned.basal[h], "%.3f", tuned.basalUnTuned[h].toString()))
                                    }
                                    layout.addView(toTableRowValue(context, "∑", totalPump, totalTuned, "%.3f", " "))
                                }
                            }
                        )
                    }
                    binding.autotuneResultsCard.visibility = if (autotunePlugin.calculationRunning && autotunePlugin.result.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun toTableRowHeader(context: Context, basal: Boolean = false): TableRow =
        TableRow(context).also { header ->
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            header.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 0 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = if (basal) rh.gs(info.nightscout.core.ui.R.string.time) else rh.gs(info.nightscout.core.ui.R.string.autotune_param)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 1 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(info.nightscout.core.ui.R.string.profile)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 2 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(info.nightscout.core.ui.R.string.autotune_tunedprofile_name)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 3 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(info.nightscout.core.ui.R.string.autotune_percent)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 4 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = if (basal) rh.gs(info.nightscout.core.ui.R.string.autotune_missing) else " "
            })
        }

    private fun toTableRowValue(context: Context, hour: String, inputValue: Double, tunedValue: Double, format: String = "%.3f", missing: String = ""): TableRow =
        TableRow(context).also { row ->
            val percentValue = Round.roundTo(tunedValue / inputValue * 100 - 100, 1.0).toInt().toString() + "%"
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 0 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = hour
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 1 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = String.format(format, inputValue)
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 2 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = String.format(format, tunedValue)
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 3 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = percentValue
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 4 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = missing
            })
        }

    private fun log(message: String) {
        autotuneFS.atLog("[Fragment] $message")
    }
}