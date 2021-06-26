package info.nightscout.androidaps.plugins.profile.local

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.databinding.LocalprofileFragmentBinding
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.ui.SpinnerHelper
import info.nightscout.androidaps.utils.ui.TimeListEdit
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.text.DecimalFormat
import javax.inject.Inject

class LocalProfileFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var basalView: TimeListEdit? = null
    private var spinner: SpinnerHelper? = null

    private val save = Runnable {
        doEdit()
        basalView?.updateLabel(resourceHelper.gs(R.string.basal_label) + ": " + sumLabel())
        localProfilePlugin.profile?.getSpecificProfile(spinner?.selectedItem.toString())?.let {
            binding.basalGraph.show(ProfileSealed.Pure(it))
        }
    }

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            localProfilePlugin.currentProfile()?.dia = SafeParse.stringToDouble(binding.dia.text.toString())
            localProfilePlugin.currentProfile()?.name = binding.name.text.toString()
            doEdit()
        }
    }

    private fun sumLabel(): String {
        val profile = localProfilePlugin.profile?.getDefaultProfile()
        val sum = profile?.let { ProfileSealed.Pure(profile).baseBasalSum() } ?: 0.0
        return " ∑" + DecimalFormatter.to2Decimal(sum) + resourceHelper.gs(R.string.insulin_unit_shortname)
    }

    private var _binding: LocalprofileFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = LocalprofileFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // activate DIA tab
        processVisibilityOnClick(binding.diaTab)
        binding.diaPlaceholder.visibility = View.VISIBLE
        // setup listeners
        binding.diaTab.setOnClickListener {
            processVisibilityOnClick(it)
            binding.diaPlaceholder.visibility = View.VISIBLE
        }
        binding.icTab.setOnClickListener {
            processVisibilityOnClick(it)
            binding.ic.visibility = View.VISIBLE
        }
        binding.isfTab.setOnClickListener {
            processVisibilityOnClick(it)
            binding.isf.visibility = View.VISIBLE
        }
        binding.basalTab.setOnClickListener {
            processVisibilityOnClick(it)
            binding.basal.visibility = View.VISIBLE
        }
        binding.targetTab.setOnClickListener {
            processVisibilityOnClick(it)
            binding.target.visibility = View.VISIBLE
        }
    }

    fun build() {
        val pumpDescription = activePlugin.activePump.pumpDescription
        if (localProfilePlugin.numOfProfiles == 0) localProfilePlugin.addNewProfile()
        val currentProfile = localProfilePlugin.currentProfile() ?: return
        val units = if (currentProfile.mgdl) Constants.MGDL else Constants.MMOL

        binding.name.removeTextChangedListener(textWatch)
        binding.name.setText(currentProfile.name)
        binding.name.addTextChangedListener(textWatch)
        binding.dia.setParams(currentProfile.dia, hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, binding.save, textWatch)
        binding.dia.tag = "LP_DIA"
        TimeListEdit(context, aapsLogger, dateUtil, view, R.id.ic, "IC", resourceHelper.gs(R.string.ic_label), currentProfile.ic, null, hardLimits.minIC(), hardLimits.maxIC(), 0.1, DecimalFormat("0.0"), save)
        basalView = TimeListEdit(context, aapsLogger, dateUtil, view, R.id.basal_holder, "BASAL", resourceHelper.gs(R.string.basal_label) + ": " + sumLabel(), currentProfile.basal, null, pumpDescription.basalMinimumRate, pumpDescription.basalMaximumRate, 0.01, DecimalFormat("0.00"), save)
        if (units == Constants.MGDL) {
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.isf, "ISF", resourceHelper.gs(R.string.isf_label), currentProfile.isf, null, HardLimits.MIN_ISF, HardLimits.MAX_ISF, 1.0, DecimalFormat("0"), save)
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.target, "TARGET", resourceHelper.gs(R.string.target_label), currentProfile.targetLow, currentProfile.targetHigh, HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), 1.0, DecimalFormat("0"), save)
        } else {
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.isf, "ISF", resourceHelper.gs(R.string.isf_label), currentProfile.isf, null, Profile.fromMgdlToUnits(HardLimits.MIN_ISF, GlucoseUnit.MMOL), Profile.fromMgdlToUnits(HardLimits.MAX_ISF, GlucoseUnit.MMOL), 0.1, DecimalFormat("0.0"), save)
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.target, "TARGET", resourceHelper.gs(R.string.target_label), currentProfile.targetLow, currentProfile.targetHigh, Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), GlucoseUnit.MMOL), Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), GlucoseUnit.MMOL), 0.1, DecimalFormat("0.0"), save)
        }

        // Spinner
        spinner = SpinnerHelper(binding.spinner)
        val profileList: ArrayList<CharSequence> = localProfilePlugin.profile?.getProfileList()
            ?: ArrayList()
        context?.let { context ->
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            spinner?.adapter = adapter
            spinner?.setSelection(localProfilePlugin.currentProfileIndex)
        } ?: return
        spinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (localProfilePlugin.isEdited) {
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.doyouwantswitchprofile), {
                            localProfilePlugin.currentProfileIndex = position
                            build()
                        }, {
                            spinner?.setSelection(localProfilePlugin.currentProfileIndex)
                        })
                    }
                } else {
                    localProfilePlugin.currentProfileIndex = position
                    build()
                }
            }
        })
        localProfilePlugin.profile?.getSpecificProfile(spinner?.selectedItem.toString())?.let {
            binding.basalGraph.show(ProfileSealed.Pure(it))
        }

        binding.profileAdd.setOnClickListener {
            if (localProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", resourceHelper.gs(R.string.saveorresetchangesfirst)) }
            } else {
                uel.log(Action.NEW_PROFILE, Sources.LocalProfile)
                localProfilePlugin.addNewProfile()
                build()
            }
        }

        binding.profileClone.setOnClickListener {
            if (localProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", resourceHelper.gs(R.string.saveorresetchangesfirst)) }
            } else {
                uel.log(Action.CLONE_PROFILE, Sources.LocalProfile, ValueWithUnit.SimpleString(localProfilePlugin.currentProfile()?.name
                    ?: ""))
                localProfilePlugin.cloneProfile()
                build()
            }
        }

        binding.profileRemove.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.deletecurrentprofile), {
                    uel.log(Action.PROFILE_REMOVED, Sources.LocalProfile, ValueWithUnit.SimpleString(localProfilePlugin.currentProfile()?.name
                        ?: ""))
                    localProfilePlugin.removeCurrentProfile()
                    build()
                }, null)
            }
        }

        // this is probably not possible because it leads to invalid profile
        // if (!pumpDescription.isTempBasalCapable) binding.basal.visibility = View.GONE

        @Suppress("SetTextI18n")
        binding.units.text = resourceHelper.gs(R.string.units_colon) + " " + (if (currentProfile.mgdl) resourceHelper.gs(R.string.mgdl) else resourceHelper.gs(R.string.mmol))

        binding.profileswitch.setOnClickListener {
            ProfileSwitchDialog()
                .also { it.arguments = Bundle().also { bundle -> bundle.putInt("profileIndex", localProfilePlugin.currentProfileIndex) } }
                .show(childFragmentManager, "NewNSTreatmentDialog")
        }

        binding.reset.setOnClickListener {
            localProfilePlugin.loadSettings()
            build()
        }

        binding.save.setOnClickListener {
            if (!localProfilePlugin.isValidEditState()) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            uel.log(Action.STORE_PROFILE, Sources.LocalProfile, ValueWithUnit.SimpleString(localProfilePlugin.currentProfile()?.name
                ?: ""))
            localProfilePlugin.storeSettings(activity)
            build()
        }
        updateGUI()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
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

    fun updateGUI() {
        if (_binding == null) return
        val isValid = localProfilePlugin.isValidEditState()
        val isEdited = localProfilePlugin.isEdited
        if (isValid) {
            this.view?.setBackgroundColor(resourceHelper.gc(R.color.ok_background))

            if (isEdited) {
                //edited profile -> save first
                binding.profileswitch.visibility = View.GONE
                binding.save.visibility = View.VISIBLE
            } else {
                binding.profileswitch.visibility = View.VISIBLE
                binding.save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(resourceHelper.gc(R.color.error_background))
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

    private fun processVisibilityOnClick(selected: View) {
        binding.diaTab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.icTab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.isfTab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.basalTab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.targetTab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        selected.setBackgroundColor(resourceHelper.gc(R.color.tabBgColorSelected))
        binding.diaPlaceholder.visibility = View.GONE
        binding.ic.visibility = View.GONE
        binding.isf.visibility = View.GONE
        binding.basal.visibility = View.GONE
        binding.target.visibility = View.GONE
    }
}
