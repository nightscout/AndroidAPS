package info.nightscout.androidaps.plugins.profile.local

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.SingleFragmentActivity
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.databinding.LocalprofileFragmentBinding
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.ui.TimeListEdit
import info.nightscout.shared.SafeParse
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject

class LocalProfileFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var inMenu = false
    private var queryingProtection = false
    private var basalView: TimeListEdit? = null

    private val save = Runnable {
        doEdit()
        basalView?.updateLabel(rh.gs(R.string.basal_label) + ": " + sumLabel())
        localProfilePlugin.getEditedProfile()?.let {
            binding.basalGraph.show(ProfileSealed.Pure(it))
            binding.icGraph.show(ProfileSealed.Pure(it))
            binding.isfGraph.show(ProfileSealed.Pure(it))
            binding.targetGraph.show(ProfileSealed.Pure(it))
            binding.insulinGraph.show(activePlugin.activeInsulin, SafeParse.stringToDouble(binding.dia.text))
        }
    }

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            localProfilePlugin.currentProfile()?.dia = SafeParse.stringToDouble(binding.dia.text)
            localProfilePlugin.currentProfile()?.name = binding.name.text.toString()
            doEdit()
        }
    }

    private fun sumLabel(): String {
        val profile = localProfilePlugin.getEditedProfile()
        val sum = profile?.let { ProfileSealed.Pure(profile).baseBasalSum() } ?: 0.0
        return " ∑" + DecimalFormatter.to2Decimal(sum) + rh.gs(R.string.insulin_unit_shortname)
    }

    private var _binding: LocalprofileFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LocalprofileFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentClass = this.activity?.let { it::class.java }
        inMenu = parentClass == SingleFragmentActivity::class.java
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
    }

    fun build() {
        val pumpDescription = activePlugin.activePump.pumpDescription
        if (localProfilePlugin.numOfProfiles == 0) localProfilePlugin.addNewProfile()
        val currentProfile = localProfilePlugin.currentProfile() ?: return
        val units = if (currentProfile.mgdl) Constants.MGDL else Constants.MMOL

        binding.name.removeTextChangedListener(textWatch)
        binding.name.setText(currentProfile.name)
        binding.name.addTextChangedListener(textWatch)
        binding.dia.setParams(currentProfile.dia, hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, null, textWatch)
        binding.dia.tag = "LP_DIA"
        TimeListEdit(
            context,
            aapsLogger,
            dateUtil,
            view,
            R.id.ic_holder,
            "IC",
            rh.gs(R.string.ic_long_label),
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
                context,
                aapsLogger,
                dateUtil,
                view,
                R.id.basal_holder,
                "BASAL",
                rh.gs(R.string.basal_long_label) + ": " + sumLabel(),
                currentProfile.basal,
                null,
                doubleArrayOf(pumpDescription.basalMinimumRate, pumpDescription.basalMaximumRate),
                null,
                0.01,
                DecimalFormat("0.00"),
                save
            )
        if (units == Constants.MGDL) {
            val isfRange = doubleArrayOf(HardLimits.MIN_ISF, HardLimits.MAX_ISF)
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.isf_holder, "ISF", rh.gs(R.string.isf_long_label), currentProfile.isf, null, isfRange, null, 1.0, DecimalFormat("0"), save)
            TimeListEdit(
                context,
                aapsLogger,
                dateUtil,
                view,
                R.id.target_holder,
                "TARGET",
                rh.gs(R.string.target_long_label),
                currentProfile.targetLow,
                currentProfile.targetHigh,
                HardLimits.VERY_HARD_LIMIT_MIN_BG,
                HardLimits.VERY_HARD_LIMIT_TARGET_BG,
                1.0,
                DecimalFormat("0"),
                save
            )
        } else {
            val isfRange = doubleArrayOf(
                roundUp(Profile.fromMgdlToUnits(HardLimits.MIN_ISF, GlucoseUnit.MMOL)),
                roundDown(Profile.fromMgdlToUnits(HardLimits.MAX_ISF, GlucoseUnit.MMOL))
            )
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.isf_holder, "ISF", rh.gs(R.string.isf_long_label), currentProfile.isf, null, isfRange, null, 0.1, DecimalFormat("0.0"), save)
            val range1 = doubleArrayOf(
                roundUp(Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_MIN_BG[0], GlucoseUnit.MMOL)),
                roundDown(Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_MIN_BG[1], GlucoseUnit.MMOL))
            )
            val range2 = doubleArrayOf(
                roundUp(Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_MAX_BG[0], GlucoseUnit.MMOL)),
                roundDown(Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_MAX_BG[1], GlucoseUnit.MMOL))
            )
            aapsLogger.info(LTag.CORE, "TimeListEdit", "build: range1" + range1[0] + " " + range1[1] + " range2" + range2[0] + " " + range2[1])
            TimeListEdit(
                context,
                aapsLogger,
                dateUtil,
                view,
                R.id.target_holder,
                "TARGET",
                rh.gs(R.string.target_long_label),
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
            val profileList: ArrayList<CharSequence> = localProfilePlugin.profile?.getProfileList() ?: ArrayList()
            binding.profileList.setAdapter(ArrayAdapter(context, R.layout.spinner_centered, profileList))
        } ?: return

        binding.profileList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (localProfilePlugin.isEdited) {
                activity?.let { activity ->
                    OKDialog.showConfirmation(
                        activity, rh.gs(R.string.doyouwantswitchprofile),
                        {
                            localProfilePlugin.currentProfileIndex = position
                            localProfilePlugin.isEdited = false
                            build()
                        }, null
                    )
                }
            } else {
                localProfilePlugin.currentProfileIndex = position
                build()
            }
        }
        localProfilePlugin.getEditedProfile()?.let {
            binding.basalGraph.show(ProfileSealed.Pure(it))
            binding.icGraph.show(ProfileSealed.Pure(it))
            binding.isfGraph.show(ProfileSealed.Pure(it))
            binding.targetGraph.show(ProfileSealed.Pure(it))
            binding.insulinGraph.show(activePlugin.activeInsulin, SafeParse.stringToDouble(binding.dia.text))
        }

        binding.profileAdd.setOnClickListener {
            if (localProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.saveorresetchangesfirst)) }
            } else {
                uel.log(Action.NEW_PROFILE, Sources.LocalProfile)
                localProfilePlugin.addNewProfile()
                build()
            }
        }

        binding.profileClone.setOnClickListener {
            if (localProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.saveorresetchangesfirst)) }
            } else {
                uel.log(
                    Action.CLONE_PROFILE, Sources.LocalProfile, ValueWithUnit.SimpleString(
                        localProfilePlugin.currentProfile()?.name
                            ?: ""
                    )
                )
                localProfilePlugin.cloneProfile()
                build()
            }
        }

        binding.profileRemove.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.deletecurrentprofile), {
                    uel.log(
                        Action.PROFILE_REMOVED, Sources.LocalProfile, ValueWithUnit.SimpleString(
                            localProfilePlugin.currentProfile()?.name
                                ?: ""
                        )
                    )
                    localProfilePlugin.removeCurrentProfile()
                    build()
                }, null)
            }
        }

        // this is probably not possible because it leads to invalid profile
        // if (!pumpDescription.isTempBasalCapable) binding.basal.visibility = View.GONE

        @Suppress("SetTextI18n")
        binding.units.text = rh.gs(R.string.units_colon) + " " + (if (currentProfile.mgdl) rh.gs(R.string.mgdl) else rh.gs(R.string.mmol))

        binding.profileswitch.setOnClickListener {
            ProfileSwitchDialog()
                .also { it.arguments = Bundle().also { bundle -> bundle.putString("profileName", localProfilePlugin.currentProfile()?.name) } }
                .show(childFragmentManager, "ProfileSwitchDialog")
        }

        binding.reset.setOnClickListener {
            localProfilePlugin.loadSettings()
            build()
        }

        binding.save.setOnClickListener {
            if (!localProfilePlugin.isValidEditState(activity)) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            uel.log(
                Action.STORE_PROFILE, Sources.LocalProfile, ValueWithUnit.SimpleString(
                    localProfilePlugin.currentProfile()?.name
                        ?: ""
                )
            )
            localProfilePlugin.storeSettings(activity)
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
        localProfilePlugin.isEdited = true
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
        val isValid = localProfilePlugin.isValidEditState(activity)
        val isEdited = localProfilePlugin.isEdited
        if (isValid) {
            this.view?.setBackgroundColor(rh.gac(context, R.attr.okBackgroundColor))
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
            this.view?.setBackgroundColor(rh.gac(context, R.attr.errorBackgroundColor))
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
        binding.ic.visibility  = (position == 1).toVisibility()
        binding.isf.visibility  = (position == 2).toVisibility()
        binding.basal.visibility  = (position == 3).toVisibility()
        binding.target.visibility  = (position == 4).toVisibility()
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
