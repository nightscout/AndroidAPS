package app.aaps.ui.activities.fragments

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.activities.fragments.TreatmentsProfileSwitchFragment.RecyclerProfileViewAdapter.ProfileSwitchViewHolder
import app.aaps.ui.databinding.TreatmentsProfileswitchFragmentBinding
import app.aaps.ui.databinding.TreatmentsProfileswitchItemBinding
import app.aaps.ui.dialogs.ProfileViewerDialog
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class TreatmentsProfileSwitchFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var _binding: TreatmentsProfileswitchFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<ProfileSealed>
    private val disposable = CompositeDisposable()
    private val millsToThePast = T.days(30).msecs()
    private var showInvalidated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsProfileswitchFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        actionHelper = ActionModeHelper(rh, activity, this)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun profileSwitchWithInvalid(now: Long) = persistenceLayer
        .getProfileSwitchesIncludingInvalidFromTime(now - millsToThePast, false)
        .map { ps -> ps.map { ProfileSealed.PS(value = it, activePlugin = null) } }

    private fun effectiveProfileSwitchWithInvalid(now: Long) = persistenceLayer
        .getEffectiveProfileSwitchesIncludingInvalidFromTime(now - millsToThePast, false)
        .map { eps -> eps.map { ProfileSealed.EPS(value = it, activePlugin = null) } }

    private fun profileSwitches(now: Long) = persistenceLayer
        .getProfileSwitchesFromTime(now - millsToThePast, false)
        .map { ps -> ps.map { ProfileSealed.PS(value = it, activePlugin = null) } }

    private fun effectiveProfileSwitches(now: Long) = persistenceLayer
        .getEffectiveProfileSwitchesFromTime(now - millsToThePast, false)
        .map { eps -> eps.map { ProfileSealed.EPS(value = it, activePlugin = null) } }

    private fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showInvalidated)
                profileSwitchWithInvalid(now)
                    .zipWith(effectiveProfileSwitchWithInvalid(now)) { first, second -> first + second }
                    .map { ml -> ml.sortedByDescending { it.timestamp } }
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        binding.recyclerview.swapAdapter(RecyclerProfileViewAdapter(list), true)
                    }
            else
                profileSwitches(now)
                    .zipWith(effectiveProfileSwitches(now)) { first, second -> first + second }
                    .map { ml -> ml.sortedByDescending { it.timestamp } }
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        binding.recyclerview.swapAdapter(RecyclerProfileViewAdapter(list), true)
                    }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        actionHelper.finish()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    inner class RecyclerProfileViewAdapter(private var profileSwitchList: List<ProfileSealed>) : RecyclerView.Adapter<ProfileSwitchViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ProfileSwitchViewHolder =
            ProfileSwitchViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_profileswitch_item, viewGroup, false))

        override fun onBindViewHolder(holder: ProfileSwitchViewHolder, position: Int) {
            val profileSwitch = profileSwitchList[position]
            holder.binding.ph.visibility = (profileSwitch is ProfileSealed.EPS).toVisibility()
            holder.binding.ns.visibility = (profileSwitch.ids?.nightscoutId != null).toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(profileSwitch.timestamp, profileSwitchList[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(profileSwitch.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeString(profileSwitch.timestamp)
            holder.binding.duration.text = rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(profileSwitch.duration ?: 0L).mins())
            holder.binding.name.text =
                if (profileSwitch is ProfileSealed.PS) profileSwitch.value.getCustomizedName(decimalFormatter) else if (profileSwitch is ProfileSealed.EPS) profileSwitch.value.originalCustomizedName else ""
            if (profileSwitch.isInProgress(dateUtil)) holder.binding.date.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.activeColor))
            else holder.binding.date.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.clone.tag = profileSwitch
            holder.binding.name.tag = profileSwitch
            holder.binding.date.tag = profileSwitch
            holder.binding.invalid.visibility = profileSwitch.isValid.not().toVisibility()
            holder.binding.duration.visibility = (profileSwitch.duration != 0L && profileSwitch.duration != null).toVisibility()
            holder.binding.cbRemove.visibility = (actionHelper.isRemoving && profileSwitch is ProfileSealed.PS).toVisibility()
            if (actionHelper.isRemoving) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    actionHelper.updateSelection(position, profileSwitch, value)
                }
                holder.binding.root.setOnClickListener {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, profileSwitch, holder.binding.cbRemove.isChecked)
                }
                holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            }
            holder.binding.clone.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
            holder.binding.spacer.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
        }

        override fun getItemCount() = profileSwitchList.size

        inner class ProfileSwitchViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsProfileswitchItemBinding.bind(itemView)

            init {
                binding.clone.setOnClickListener {
                    activity?.let { activity ->
                        val profileSwitch = (it.tag as ProfileSealed.PS).value
                        val profileSealed = it.tag as ProfileSealed
                        OKDialog.showConfirmation(
                            activity,
                            rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
                            rh.gs(app.aaps.core.ui.R.string.copytolocalprofile) + "\n" + profileSwitch.getCustomizedName(decimalFormatter) + "\n" + dateUtil.dateAndTimeString(profileSwitch.timestamp),
                            Runnable {
                                uel.log(
                                    action = Action.PROFILE_SWITCH_CLONED, source = Sources.Treatments,
                                    note = profileSwitch.getCustomizedName(decimalFormatter) + " " + dateUtil.dateAndTimeString(profileSwitch.timestamp).replace(".", "_"),
                                    listValues = listOf(
                                        ValueWithUnit.Timestamp(profileSwitch.timestamp),
                                        ValueWithUnit.SimpleString(profileSwitch.profileName)
                                    )
                                )
                                val nonCustomized = profileSealed.convertToNonCustomizedProfile(dateUtil)
                                activePlugin.activeProfileSource.addProfile(
                                    activePlugin.activeProfileSource.copyFrom(
                                        nonCustomized,
                                        profileSwitch.getCustomizedName(decimalFormatter) + " " + dateUtil.dateAndTimeString(profileSwitch.timestamp).replace(".", "_")
                                    )
                                )
                                rxBus.send(EventLocalProfileChanged())
                            })
                    }
                }
                binding.clone.paintFlags = binding.clone.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.name.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSealed).timestamp)
                            args.putInt("mode", UiInteraction.Mode.RUNNING_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
                binding.date.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSealed).timestamp)
                            args.putInt("mode", UiInteraction.Mode.RUNNING_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_profile_switch, menu)
        updateMenuVisibility()
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items -> actionHelper.startRemove()

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.hide_invalidated_records)
                swapAdapter()
                true
            }

            else -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<ProfileSealed>): String {
        if (selectedItems.size() == 1) {
            val profileSwitch = selectedItems.valueAt(0)
            return rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch) + ": " + profileSwitch.profileName + "\n" + rh.gs(app.aaps.core.ui.R.string.date) + ": " + dateUtil.dateAndTimeString(
                profileSwitch.timestamp
            )
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<ProfileSealed>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, profileSwitch ->
                    disposable += persistenceLayer.invalidateProfileSwitch(
                        profileSwitch.id,
                        Action.PROFILE_SWITCH_REMOVED, Sources.Treatments, profileSwitch.profileName,
                        listOf(ValueWithUnit.Timestamp(profileSwitch.timestamp))
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
