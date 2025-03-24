package app.aaps.plugins.insulin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.insulin.databinding.InsulinFragmentBinding
import app.aaps.plugins.insulin.databinding.InsulinProfileItemBinding
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerFragment
import org.json.JSONObject
import java.text.DecimalFormat
import javax.inject.Inject

class InsulinFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var insulinPlugin: InsulinPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var instantiator: Instantiator
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var dateUtil: DateUtil

    private var _binding: InsulinFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val currentInsulin: ICfg
        get() = insulinPlugin.currentInsulin
    private var selectedTemplate = Insulin.InsulinType.OREF_RAPID_ACTING    // Default Insulin (should only be used on new install
    private var minPeak = Insulin.InsulinType.OREF_RAPID_ACTING.peak.toDouble()
    private var maxPeak = Insulin.InsulinType.OREF_RAPID_ACTING.peak.toDouble()
    private lateinit var profileStore: ProfileStore
    private var profileList = ArrayList<String>()

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            currentInsulin.insulinLabel = binding.name.text.toString()
            currentInsulin.setPeak(SafeParse.stringToInt(binding.peak.text))
            currentInsulin.setDia(SafeParse.stringToDouble(binding.dia.text))
            doEdit()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InsulinFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        profileStore = activePlugin.activeProfileSource.profile ?: instantiator.provideProfileStore(JSONObject())
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(context)
        binding.insulinList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (insulinPlugin.isEdited) {
                activity?.let { activity ->
                    OKDialog.showConfirmation(
                        activity, rh.gs(R.string.do_you_want_switch_insulin),
                        {
                            insulinPlugin.currentInsulinIndex = position
                            insulinPlugin.currentInsulin = insulinPlugin.currentInsulin().deepClone()
                            insulinPlugin.isEdited = false
                            swapAdapter()
                            build()
                        }, null
                    )
                }
            } else {
                insulinPlugin.currentInsulinIndex = position
                insulinPlugin.currentInsulin = insulinPlugin.currentInsulin().deepClone()
                swapAdapter()
                build()
            }
        }
        if (insulinPlugin.numOfInsulins == 0) {
            insulinPlugin.addNewInsulin(
                ICfg("", selectedTemplate.peak, selectedTemplate.dia),
                autoName = true
            )
        }
        insulinPlugin.setCurrent(insulinPlugin.iCfg)
        swapAdapter()

        processVisibility(0)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                processVisibility(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.insulinTemplate.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedTemplate = insulinFromTemplate(insulinPlugin.insulinTemplateList()[position].toString())
            currentInsulin.setPeak(selectedTemplate.peak)
            currentInsulin.setDia(selectedTemplate.dia)
            insulinPlugin.isEdited = true
            doEdit()
        }
        binding.insulinAdd.setOnClickListener {
            if (insulinPlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.save_or_reset_changes_first)) }
            } else {
                insulinPlugin.addNewInsulin(
                    ICfg(
                        insulinLabel = "",              // Let plugin propose a new unique name from template
                        peak = selectedTemplate.peak,
                        dia = selectedTemplate.dia
                    )
                )
                insulinPlugin.isEdited = true
                swapAdapter()
                build()
            }
        }
        binding.insulinRemove.setOnClickListener {
            if (insulinPlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.save_or_reset_changes_first)) }
            } else {
                if (insulinPlugin.currentInsulinIndex != insulinPlugin.defaultInsulinIndex) {
                    insulinPlugin.removeCurrentInsulin(activity)
                    insulinPlugin.isEdited = false
                }
                swapAdapter()
                build()
            }
        }
        binding.reset.setOnClickListener {
            insulinPlugin.currentInsulin = insulinPlugin.currentInsulin().deepClone()
            insulinPlugin.isEdited = false
            build()
        }
        binding.save.setOnClickListener {
            if (!insulinPlugin.isValidEditState(activity)) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            uel.log(
                action = Action.STORE_INSULIN, source = Sources.Insulin,
                value = ValueWithUnit.SimpleString(insulinPlugin.currentInsulin().insulinLabel)
            )
            if (profileList.isEmpty()) {
                insulinPlugin.insulins[insulinPlugin.currentInsulinIndex] = currentInsulin
                insulinPlugin.storeSettings()
                build()
            } else {
                OKDialog.showConfirmation(
                    requireContext(),
                    rh.gs(R.string.update_profiles_title),
                    rh.gs(R.string.update_profiles_message, profileList.size),
                    Runnable {
                        insulinPlugin.insulins[insulinPlugin.currentInsulinIndex] = currentInsulin
                        insulinPlugin.storeSettings()
                        insulinPlugin.updateProfiles(profileList, profileStore, dateUtil.now())
                        profileStore = activePlugin.activeProfileSource.profile ?: instantiator.provideProfileStore(JSONObject())
                        swapAdapter()
                        build()
                    }
                )
            }
        }
        binding.autoName.setOnClickListener {
            binding.name.setText(insulinPlugin.createNewInsulinLabel(currentInsulin, includingCurrent = false))
            insulinPlugin.isEdited = true
            build()
        }

        val insulinTemplateList: ArrayList<CharSequence> = insulinPlugin.insulinTemplateList()
        context?.let { context ->
            binding.insulinTemplate.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, insulinTemplateList))
        } ?: return
        binding.insulinTemplate.setText(currentInsulin.let { rh.gs(Insulin.InsulinType.fromPeak(it.insulinPeakTime).label) }, false)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        build()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGUI() {
        if (_binding == null) return
        val isValid = insulinPlugin.isValidEditState(activity)
        val isEdited = insulinPlugin.isEdited
        val insulinList: ArrayList<CharSequence> = insulinPlugin.insulinList()
        context?.let { context ->
            binding.insulinList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, insulinList))
        } ?: return
        binding.insulinList.setText(insulinPlugin.currentInsulin.insulinLabel, false)
        if (isValid) {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.okBackgroundColor))
            binding.insulinList.isEnabled = true

            if (isEdited) {
                //edited insulin -> save first
                binding.save.visibility = View.VISIBLE
            } else {
                binding.save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.errorBackgroundColor))
            binding.insulinList.isEnabled = false
            binding.save.visibility = View.GONE //don't save an invalid profile
        }

        //Show reset button if data was edited
        if (isEdited) {
            binding.reset.visibility = View.VISIBLE
        } else {
            binding.reset.visibility = View.GONE
        }
        //Show Remove button if profileList is empty
        if (profileList.isEmpty)
            binding.insulinRemove.visibility = View.VISIBLE
        else
            binding.insulinRemove.visibility = View.GONE
        binding.graph.show(activePlugin.activeInsulin, currentInsulin)
    }

    fun build() {
        binding.insulinTemplate.setText(rh.gs(Insulin.InsulinType.fromPeak(currentInsulin.insulinPeakTime).label), false)
        binding.name.removeTextChangedListener(textWatch)
        binding.name.setText(currentInsulin.insulinLabel)
        binding.name.addTextChangedListener(textWatch)
        binding.insulinList.filters = arrayOf()
        binding.insulinList.setText(currentInsulin.insulinLabel)

        when (selectedTemplate) {
            Insulin.InsulinType.OREF_FREE_PEAK -> {
                minPeak = hardLimits.minPeak().toDouble()
                maxPeak = hardLimits.maxPeak().toDouble()
            }

            else                               -> {
                minPeak = currentInsulin.getPeak().toDouble()
                maxPeak = minPeak
            }
        }
        binding.peak.setParams(currentInsulin.getPeak().toDouble(), minPeak, maxPeak, 1.0, DecimalFormat("0"), false, null, textWatch)
        binding.dia.setParams(currentInsulin.getDia(), hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, null, textWatch)

        updateGUI()
    }

    fun insulinFromTemplate(label: String): Insulin.InsulinType = Insulin.InsulinType.entries.firstOrNull { rh.gs(it.label) == label } ?: Insulin.InsulinType.OREF_FREE_PEAK

    fun doEdit() {
        insulinPlugin.isEdited = true
        updateGUI()
    }

    private fun processVisibility(position: Int) {
        binding.insulinPlaceholder.visibility = (position == 0).toVisibility()
        binding.profilePlaceholder.visibility = (position == 1).toVisibility()
    }

    private fun swapAdapter() {
        profileList = profileStore.getProfileList(insulinPlugin.currentInsulin())
        if (insulinPlugin.currentInsulin().isEqual(insulinPlugin.iCfg))
            profileList.add(0, rh.gs(app.aaps.core.ui.R.string.active))
        binding.tabLayout.getTabAt(1)?.text = rh.gs(app.aaps.core.ui.R.string.profile_number, profileList.size)
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(profileList), true)
    }

    inner class RecyclerViewAdapter internal constructor(private var profileList: List<String>) : RecyclerView.Adapter<RecyclerViewAdapter.ProfileViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ProfileViewHolder =
            ProfileViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.insulin_profile_item, viewGroup, false))

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val record = this@RecyclerViewAdapter.profileList[position]
            holder.binding.profileName.text = record

        }

        override fun getItemCount(): Int {
            return profileList.size
        }

        inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = InsulinProfileItemBinding.bind(itemView)

            init {
                binding.profileName.setOnClickListener {
                    activity?.let { activity ->
                        val profileName = (it as TextView).text.toString()
                        val selectedProfile = if ((it as TextView).text.toString()==rh.gs(app.aaps.core.ui.R.string.active))
                            profileFunction.getProfile()?.convertToNonCustomizedProfile(dateUtil)
                        else
                            profileStore.getSpecificProfile((it as TextView).text.toString())
                        selectedProfile?.let { profile ->
                            uiInteraction.runProfileViewerDialog(
                                fragmentManager = childFragmentManager,
                                time = dateUtil.now(),
                                mode = UiInteraction.Mode.CUSTOM_PROFILE,
                                customProfile = profile.jsonObject.toString(),
                                customProfileName = profileName
                            )
                        }
                    }
                }
            }
        }
    }
}