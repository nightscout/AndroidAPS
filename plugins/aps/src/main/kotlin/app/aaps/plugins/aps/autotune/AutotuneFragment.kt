package app.aaps.plugins.aps.autotune

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
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.LocalInsulin
import app.aaps.plugins.aps.autotune.events.EventAutotuneUpdateGui
import app.aaps.plugins.aps.databinding.AutotuneFragmentBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

class AutotuneFragment : DaggerFragment() {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var autotuneFS: AutotuneFS
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var loop: Loop
    @Inject lateinit var profileStoreProvider: Provider<ProfileStore>
    @Inject lateinit var atProfileProvider: Provider<ATProfile>

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
        preferences.put(BooleanKey.AutotuneTuneInsulinCurve, false)  // put to false tune insulin curve
        preferences.put(BooleanKey.AutotuneAdditionalLog, false)      // put to false additional log
        autotunePlugin.loadLastRun()
        if (autotunePlugin.lastNbDays.isEmpty())
            autotunePlugin.lastNbDays = preferences.get(IntKey.AutotuneDefaultTuneDays).toString()
        val defaultValue = preferences.get(IntKey.AutotuneDefaultTuneDays).toDouble()
        profileStore = activePlugin.activeProfileSource.profile ?: profileStoreProvider.get().with(JSONObject())
        profileName = if (binding.profileList.text.toString() == rh.gs(app.aaps.core.ui.R.string.active)) "" else binding.profileList.text.toString()
        profileFunction.getProfile()?.let { currentProfile ->
            profile = atProfileProvider.get().with(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile, LocalInsulin(""))
        }
        days.addToLayout(binding.selectWeekDays)
        days.view?.setOnWeekdaysChangeListener { i: Int, selected: Boolean ->
            if (autotunePlugin.calculationRunning)
                days.view?.setSelectedDays(days.getSelectedDays())
            else {
                days[WeekDay.DayOfWeek.fromCalendarInt(i)] = selected
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
                profileName = if (binding.profileList.text.toString() == rh.gs(app.aaps.core.ui.R.string.active)) "" else binding.profileList.text.toString()
                profileFunction.getProfile()?.let { currentProfile ->
                    profile = atProfileProvider.get().with(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile, LocalInsulin(""))
                }
                autotunePlugin.selectedProfile = profileName
                resetParam(true)
                binding.tuneDays.value = autotunePlugin.lastNbDays.toDouble()
            }
            updateGui()
        }

        binding.autotuneCopyLocal.setOnClickListener {
            val localName = rh.gs(R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(autotunePlugin.lastRun)
            val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
            autotunePlugin.tunedProfile?.let { tunedProfile ->
                OKDialog.showConfirmation(
                    requireContext(),
                    rh.gs(R.string.autotune_copy_localprofile_button),
                    rh.gs(R.string.autotune_copy_local_profile_message) + "\n" + localName,
                    {
                        val profilePlugin = activePlugin.activeProfileSource
                        profilePlugin.addProfile(profilePlugin.copyFrom(tunedProfile.getProfile(circadian), localName))
                        rxBus.send(EventLocalProfileChanged())
                        uel.log(
                            action = Action.NEW_PROFILE,
                            source = Sources.Autotune,
                            value = ValueWithUnit.SimpleString(localName)
                        )
                        updateGui()
                    })
            }
        }

        binding.autotuneUpdateProfile.setOnClickListener {
            val localName = autotunePlugin.pumpProfile.profileName
            OKDialog.showConfirmation(
                requireContext(),
                rh.gs(R.string.autotune_update_input_profile_button),
                rh.gs(R.string.autotune_update_local_profile_message, localName),
                {
                    autotunePlugin.tunedProfile?.profileName = localName
                    autotunePlugin.updateProfile(autotunePlugin.tunedProfile)
                    autotunePlugin.updateButtonVisibility = View.GONE
                    autotunePlugin.saveLastRun()
                    uel.log(
                        action = Action.STORE_PROFILE,
                        source = Sources.Autotune,
                        value = ValueWithUnit.SimpleString(localName)
                    )
                    updateGui()
                }
            )
        }

        binding.autotuneRevertProfile.setOnClickListener {
            val localName = autotunePlugin.pumpProfile.profileName
            OKDialog.showConfirmation(
                requireContext(),
                rh.gs(R.string.autotune_revert_input_profile_button),
                rh.gs(R.string.autotune_revert_local_profile_message, localName),
                {
                    autotunePlugin.tunedProfile?.profileName = ""
                    autotunePlugin.updateProfile(autotunePlugin.pumpProfile)
                    autotunePlugin.updateButtonVisibility = View.VISIBLE
                    autotunePlugin.saveLastRun()
                    uel.log(
                        action = Action.STORE_PROFILE,
                        source = Sources.Autotune,
                        value = ValueWithUnit.SimpleString(localName)
                    )
                    updateGui()
                }
            )
        }

        binding.autotuneCheckInputProfile.setOnClickListener {
            val pumpProfile = profileFunction.getProfile()?.let { currentProfile ->
                profileStore.getSpecificProfile(profileName)?.let { specificProfile ->
                    atProfileProvider.get().with(ProfileSealed.Pure(specificProfile, null), LocalInsulin("")).also {
                        it.profileName = profileName
                    }
                }
                    ?: atProfileProvider.get().with(currentProfile, LocalInsulin("")).also {
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
            val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
            val tunedProfile = if (circadian) autotunePlugin.tunedProfile?.circadianProfile else autotunePlugin.tunedProfile?.profile
            uiInteraction.runProfileViewerDialog(
                fragmentManager = childFragmentManager,
                time = dateUtil.now(),
                mode = UiInteraction.Mode.PROFILE_COMPARE,
                customProfile = pumpProfile.profile.toPureNsJson(dateUtil).toString(),
                customProfileName = pumpProfile.profileName + "\n" + rh.gs(R.string.autotune_tunedprofile_name),
                customProfile2 = tunedProfile?.toPureNsJson(dateUtil).toString()
            )
        }

        binding.autotuneProfileswitch.setOnClickListener {
            val tunedProfile = autotunePlugin.tunedProfile
            autotunePlugin.updateProfile(tunedProfile)
            val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
            if (loop.runningMode == RM.Mode.DISCONNECTED_PUMP) {
                activity?.let { it1 -> OKDialog.show(it1, rh.gs(R.string.not_available_full), rh.gs(R.string.pump_disconnected)) }
            } else {
                tunedProfile?.let { tunedP ->
                    tunedP.profileStore(circadian)?.let {
                        OKDialog.showConfirmation(
                            requireContext(),
                            rh.gs(app.aaps.core.ui.R.string.activate_profile) + ": " + tunedP.profileName + "?",
                            {
                                uel.log(
                                    action = Action.STORE_PROFILE,
                                    source = Sources.Autotune,
                                    value = ValueWithUnit.SimpleString(tunedP.profileName)
                                )
                                val now = dateUtil.now()
                                profileFunction.createProfileSwitch(
                                    profileStore = it,
                                    profileName = tunedP.profileName,
                                    durationInMinutes = 0,
                                    percentage = 100,
                                    timeShiftInHours = 0,
                                    timestamp = now,
                                    action = Action.PROFILE_SWITCH,
                                    source = Sources.Autotune,
                                    note = "Autotune AutoSwitch",
                                    listValues = listOf(ValueWithUnit.SimpleString(autotunePlugin.tunedProfile!!.profileName))
                                )
                                rxBus.send(EventLocalProfileChanged())
                                updateGui()
                            }
                        )
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGui() {
        _binding ?: return
        profileStore = activePlugin.activeProfileSource.profile ?: profileStoreProvider.get().with(JSONObject())
        profileName = if (binding.profileList.text.toString() == rh.gs(app.aaps.core.ui.R.string.active)) "" else binding.profileList.text.toString()
        profileFunction.getProfile()?.let { currentProfile ->
            profile = atProfileProvider.get().with(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile, LocalInsulin(""))
        }
        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        profileList.add(0, rh.gs(app.aaps.core.ui.R.string.active))
        context?.let { context ->
            binding.profileList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, profileList))
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
                binding.tuneWarning.text = rh.gs(R.string.autotune_warning_during_run)
            }

            autotunePlugin.lastRunSuccess     -> {
                binding.autotuneCopyLocal.visibility = View.VISIBLE
                binding.autotuneUpdateProfile.visibility = autotunePlugin.updateButtonVisibility
                binding.autotuneRevertProfile.visibility = if (autotunePlugin.updateButtonVisibility == View.VISIBLE) View.GONE else View.VISIBLE
                binding.autotuneProfileswitch.visibility = View.VISIBLE
                binding.tuneWarning.text = rh.gs(R.string.autotune_warning_after_run)
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
            binding.tuneWarning.text = rh.gs(R.string.autotune_warning_after_run)
        } else if (!runToday || autotunePlugin.result.isEmpty()) //if new day re-init result, default days, warning and button's visibility
            resetParam(!runToday)
    }

    private fun addWarnings(): String {
        var warning = ""
        var nl = ""
        if (profileFunction.getProfile() == null) {
            warning = rh.gs(app.aaps.core.ui.R.string.profileswitch_ismissing)
            return warning
        }
        profileFunction.getProfile()?.let { currentProfile ->
            profile =
                atProfileProvider.get().with(profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile, LocalInsulin("")).also { profile ->
                    if (!profile.isValid) return rh.gs(R.string.autotune_profile_invalid)
                    if (profile.icSize > 1) {
                        warning += nl + rh.gs(R.string.autotune_ic_warning, profile.icSize, profile.ic)
                        nl = "\n"
                    }
                    if (profile.isfSize > 1) {
                        warning += nl + rh.gs(R.string.autotune_isf_warning, profile.isfSize, profileUtil.fromMgdlToUnits(profile.isf), profileFunction.getUnits().asText)
                    }
                }
        }
        return warning
    }

    private fun resetParam(resetDay: Boolean) {
        binding.tuneWarning.text = addWarnings()
        if (resetDay) {
            autotunePlugin.lastNbDays = preferences.get(IntKey.AutotuneDefaultTuneDays).toString()
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
                                    val tuneInsulin = preferences.get(BooleanKey.AutotuneTuneInsulinCurve)
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
                                                rh.gs(app.aaps.core.ui.R.string.dia),
                                                Round.roundTo(autotunePlugin.pumpProfile.localInsulin.dia, 0.1),
                                                Round.roundTo(tuned.localInsulin.dia, 0.1),
                                                "%.1f"
                                            )
                                        )
                                    }
                                    layout.addView(
                                        toTableRowValue(
                                            context,
                                            rh.gs(app.aaps.core.ui.R.string.isf_short),
                                            Round.roundTo(autotunePlugin.pumpProfile.isf / toMgDl, 0.001),
                                            Round.roundTo(tuned.isf / toMgDl, 0.001),
                                            isfFormat
                                        )
                                    )
                                    layout.addView(
                                        toTableRowValue(
                                            context,
                                            rh.gs(app.aaps.core.ui.R.string.ic_short),
                                            Round.roundTo(autotunePlugin.pumpProfile.ic, 0.001),
                                            Round.roundTo(tuned.ic, 0.001),
                                            "%.2f"
                                        )
                                    )
                                    layout.addView(
                                        TextView(context).apply {
                                            text = rh.gs(app.aaps.core.ui.R.string.basal)
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
                text = if (basal) rh.gs(app.aaps.core.ui.R.string.time) else rh.gs(R.string.autotune_param)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 1 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(app.aaps.core.ui.R.string.profile)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 2 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(R.string.autotune_tunedprofile_name)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 3 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = rh.gs(R.string.autotune_percent)
            })
            header.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 4 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = if (basal) rh.gs(R.string.autotune_missing) else " "
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
                text = String.format(Locale.getDefault(), format, inputValue)
            })
            row.addView(TextView(context).apply {
                layoutParams = lp.apply { column = 2 }
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text = String.format(Locale.getDefault(), format, tunedValue)
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