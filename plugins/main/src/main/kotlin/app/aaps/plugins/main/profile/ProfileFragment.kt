package app.aaps.plugins.main.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ProfileFragmentBinding
import app.aaps.plugins.main.profile.ui.TimeListEdit
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profilePlugin: ProfilePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var loop: Loop

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var inMenu = false
    private var queryingProtection = false
    private var basalView: TimeListEdit? = null

    private val save = Runnable {
        doEdit()
        basalView?.updateLabel(rh.gs(app.aaps.core.ui.R.string.basal_label) + ": " + sumLabel())
        profilePlugin.getEditedProfile()?.let {
            binding.basalGraph.show(ProfileSealed.Pure(it, null))
            binding.icGraph.show(ProfileSealed.Pure(it, null))
            binding.isfGraph.show(ProfileSealed.Pure(it, null))
            binding.targetGraph.show(ProfileSealed.Pure(it, null))
            binding.insulinGraph.show(activePlugin.activeInsulin, SafeParse.stringToDouble(binding.dia.text))
        }
    }

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            profilePlugin.currentProfile()?.dia = SafeParse.stringToDouble(binding.dia.text)
            profilePlugin.currentProfile()?.name = binding.name.text.toString()
            doEdit()
        }
    }

    private fun sumLabel(): String {
        val profile = profilePlugin.getEditedProfile()
        val sum = profile?.let { ProfileSealed.Pure(profile, null).baseBasalSum() } ?: 0.0
        return " ∑" + decimalFormatter.to2Decimal(sum) + " " + rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
    }

    private var _binding: ProfileFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ProfileFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentClass = this.activity?.let { it::class.java }
        inMenu = parentClass == uiInteraction.singleFragmentActivity
        updateProtectedUi()
        processVisibility(0)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                processVisibility(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.diaLabel.labelFor = binding.dia.editTextId
        binding.unlock.setOnClickListener { queryProtection() }

        val profiles = profilePlugin.profile?.getProfileList() ?: ArrayList()
        val activeProfile = profileFunction.getProfileName()
        val profileIndex = profiles.indexOf(activeProfile)
        profilePlugin.currentProfileIndex = if (profileIndex >= 0) profileIndex else 0
        val aps = activePlugin.activeAPS
        binding.isfDynamicLabel.visibility = aps.supportsDynamicIsf().toVisibility()
        binding.icDynamicLabel.visibility = aps.supportsDynamicIc().toVisibility()
    }

    fun build() {
        val pumpDescription = activePlugin.activePump.pumpDescription
        if (profilePlugin.numOfProfiles == 0) profilePlugin.addNewProfile()
        val currentProfile = profilePlugin.currentProfile() ?: return
        val units = if (currentProfile.mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText

        binding.name.removeTextChangedListener(textWatch)
        binding.name.setText(currentProfile.name)
        binding.name.addTextChangedListener(textWatch)
        binding.profileList.filters = arrayOf()
        binding.profileList.setText(currentProfile.name)
        binding.dia.setParams(currentProfile.dia, hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, null, textWatch)
        binding.dia.tag = "LP_DIA"
        TimeListEdit(
            requireContext(),
            aapsLogger,
            dateUtil,
            requireView(),
            R.id.ic_holder,
            "IC",
            rh.gs(app.aaps.core.ui.R.string.ic_long_label),
            currentProfile.ic,
            null,
            doubleArrayOf(hardLimits.minIC(), hardLimits.maxIC()),
            null,
            0.1,
            DecimalFormat("0.0"),
            save
        )
        basalView =
            TimeListEdit(
                requireContext(),
                aapsLogger,
                dateUtil,
                requireView(),
                R.id.basal_holder,
                "BASAL",
                rh.gs(app.aaps.core.ui.R.string.basal_long_label) + ": " + sumLabel(),
                currentProfile.basal,
                null,
                doubleArrayOf(pumpDescription.basalMinimumRate, pumpDescription.basalMaximumRate),
                null,
                0.01,
                DecimalFormat("0.00"),
                save
            )
        if (units == GlucoseUnit.MGDL.asText) {
            val isfRange = doubleArrayOf(HardLimits.MIN_ISF, HardLimits.MAX_ISF)
            TimeListEdit(
                requireContext(),
                aapsLogger,
                dateUtil,
                requireView(),
                R.id.isf_holder,
                "ISF",
                rh.gs(app.aaps.core.ui.R.string.isf_long_label),
                currentProfile.isf,
                null,
                isfRange,
                null,
                1.0,
                DecimalFormat("0"),
                save
            )
            TimeListEdit(
                requireContext(),
                aapsLogger,
                dateUtil,
                requireView(),
                R.id.target_holder,
                "TARGET",
                rh.gs(app.aaps.core.ui.R.string.target_long_label),
                currentProfile.targetLow,
                currentProfile.targetHigh,
                HardLimits.LIMIT_MIN_BG,
                HardLimits.LIMIT_TARGET_BG,
                1.0,
                DecimalFormat("0"),
                save
            )
        } else {
            val isfRange = doubleArrayOf(
                roundUp(profileUtil.fromMgdlToUnits(HardLimits.MIN_ISF, GlucoseUnit.MMOL)),
                roundDown(profileUtil.fromMgdlToUnits(HardLimits.MAX_ISF, GlucoseUnit.MMOL))
            )
            TimeListEdit(
                requireContext(), aapsLogger, dateUtil, requireView(), R.id.isf_holder, "ISF", rh.gs(app.aaps.core.ui.R.string.isf_long_label), currentProfile.isf, null, isfRange, null, 0.1,
                DecimalFormat
                    ("0.0"), save
            )
            val range1 = doubleArrayOf(
                roundUp(profileUtil.fromMgdlToUnits(HardLimits.LIMIT_MIN_BG[0], GlucoseUnit.MMOL)),
                roundDown(profileUtil.fromMgdlToUnits(HardLimits.LIMIT_MIN_BG[1], GlucoseUnit.MMOL))
            )
            val range2 = doubleArrayOf(
                roundUp(profileUtil.fromMgdlToUnits(HardLimits.LIMIT_MAX_BG[0], GlucoseUnit.MMOL)),
                roundDown(profileUtil.fromMgdlToUnits(HardLimits.LIMIT_MAX_BG[1], GlucoseUnit.MMOL))
            )
            aapsLogger.info(LTag.CORE, "TimeListEdit", "build: range1" + range1[0] + " " + range1[1] + " range2" + range2[0] + " " + range2[1])
            TimeListEdit(
                requireContext(),
                aapsLogger,
                dateUtil,
                requireView(),
                R.id.target_holder,
                "TARGET",
                rh.gs(app.aaps.core.ui.R.string.target_long_label),
                currentProfile.targetLow,
                currentProfile.targetHigh,
                range1,
                range2,
                0.1,
                DecimalFormat("0.0"),
                save
            )
        }

        context?.let { context ->
            val profileList: ArrayList<CharSequence> = profilePlugin.profile?.getProfileList() ?: ArrayList()
            binding.profileList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, profileList))
        } ?: return

        binding.profileList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (profilePlugin.isEdited) {
                activity?.let { activity ->
                    OKDialog.showConfirmation(
                        activity, rh.gs(R.string.do_you_want_switch_profile),
                        {
                            profilePlugin.currentProfileIndex = position
                            profilePlugin.isEdited = false
                            build()
                        }, null
                    )
                }
            } else {
                profilePlugin.currentProfileIndex = position
                build()
            }
        }
        profilePlugin.getEditedProfile()?.let {
            binding.basalGraph.show(ProfileSealed.Pure(it, null))
            binding.icGraph.show(ProfileSealed.Pure(it, null))
            binding.isfGraph.show(ProfileSealed.Pure(it, null))
            binding.targetGraph.show(ProfileSealed.Pure(it, null))
            binding.insulinGraph.show(activePlugin.activeInsulin, SafeParse.stringToDouble(binding.dia.text))
        }

        binding.profileAdd.setOnClickListener {
            if (profilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.save_or_reset_changes_first)) }
            } else {
                uel.log(Action.NEW_PROFILE, Sources.LocalProfile)
                profilePlugin.addNewProfile()
                build()
            }
        }

        binding.profileClone.setOnClickListener {
            if (profilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.save_or_reset_changes_first)) }
            } else {
                uel.log(
                    action = Action.CLONE_PROFILE, source = Sources.LocalProfile,
                    value = ValueWithUnit.SimpleString(profilePlugin.currentProfile()?.name ?: "")
                )
                profilePlugin.cloneProfile()
                build()
            }
        }

        binding.profileRemove.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.delete_current_profile, profilePlugin.currentProfile()?.name), {
                    uel.log(
                        action = Action.PROFILE_REMOVED, source = Sources.LocalProfile,
                        value = ValueWithUnit.SimpleString(profilePlugin.currentProfile()?.name ?: "")
                    )
                    profilePlugin.removeCurrentProfile()
                    build()
                }, null)
            }
        }

        // this is probably not possible because it leads to invalid profile
        // if (!pumpDescription.isTempBasalCapable) binding.basal.visibility = View.GONE

        @Suppress("SetTextI18n")
        binding.units.text = rh.gs(R.string.units_colon) + " " + (if (currentProfile.mgdl) rh.gs(app.aaps.core.ui.R.string.mgdl) else rh.gs(app.aaps.core.ui.R.string.mmol))

        binding.profileswitch.setOnClickListener {
            if (loop.runningMode == RM.Mode.DISCONNECTED_PUMP) {
                activity?.let { activity -> OKDialog.show(activity, rh.gs(R.string.not_available_full), rh.gs(R.string.smscommunicator_pump_disconnected)) }
            } else {
                uiInteraction.runProfileSwitchDialog(childFragmentManager, profilePlugin.currentProfile()?.name)
            }
        }

        binding.reset.setOnClickListener {
            profilePlugin.loadSettings()
            build()
        }

        binding.save.setOnClickListener {
            if (!profilePlugin.isValidEditState(activity)) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            uel.log(
                action = Action.STORE_PROFILE, source = Sources.LocalProfile,
                value = ValueWithUnit.SimpleString(profilePlugin.currentProfile()?.name ?: "")
            )
            profilePlugin.storeSettings(activity, dateUtil.now())
            build()
        }
        updateGUI()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        if (inMenu) queryProtection() else updateProtectedUi()
        disposable += rxBus
            .toObservable(EventLocalProfileChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ build() }, fabricPrivacy::logException)
        build()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun doEdit() {
        profilePlugin.isEdited = true
        updateGUI()
    }

    private fun roundUp(number: Double): Double {
        return number.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
    }

    private fun roundDown(number: Double): Double {
        return number.toBigDecimal().setScale(1, RoundingMode.DOWN).toDouble()
    }

    private fun updateGUI() {
        if (_binding == null) return
        val isValid = profilePlugin.isValidEditState(activity)
        val isEdited = profilePlugin.isEdited
        if (isValid) {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.okBackgroundColor))
            binding.profileList.isEnabled = true

            if (isEdited) {
                //edited profile -> save first
                binding.profileswitch.visibility = View.GONE
                binding.save.visibility = View.VISIBLE
            } else {
                binding.profileswitch.visibility = View.VISIBLE
                binding.save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.errorBackgroundColor))
            binding.profileList.isEnabled = false
            binding.profileswitch.visibility = View.GONE
            binding.save.visibility = View.GONE //don't save an invalid profile
        }

        //Show reset button if data was edited
        if (isEdited) {
            binding.reset.visibility = View.VISIBLE
        } else {
            binding.reset.visibility = View.GONE
        }
    }

    private fun processVisibility(position: Int) {
        binding.diaPlaceholder.visibility = (position == 0).toVisibility()
        binding.ic.visibility = (position == 1).toVisibility()
        binding.isf.visibility = (position == 2).toVisibility()
        binding.basal.visibility = (position == 3).toVisibility()
        binding.target.visibility = (position == 4).toVisibility()
    }

    private fun updateProtectedUi() {
        _binding ?: return
        val isLocked = protectionCheck.isLocked(ProtectionCheck.Protection.PREFERENCES)
        binding.mainLayout.visibility = isLocked.not().toVisibility()
        binding.unlock.visibility = isLocked.toVisibility()
    }

    private fun queryProtection() {
        val isLocked = protectionCheck.isLocked(ProtectionCheck.Protection.PREFERENCES)
        if (isLocked && !queryingProtection) {
            activity?.let { activity ->
                queryingProtection = true
                val doUpdate = { activity.runOnUiThread { queryingProtection = false; updateProtectedUi() } }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, doUpdate, doUpdate, doUpdate)
            }
        }
    }
}