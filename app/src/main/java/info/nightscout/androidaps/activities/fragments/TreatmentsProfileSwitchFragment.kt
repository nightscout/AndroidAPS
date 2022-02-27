package info.nightscout.androidaps.activities.fragments

import android.graphics.Paint
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import android.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.util.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.fragments.TreatmentsProfileSwitchFragment.RecyclerProfileViewAdapter.ProfileSwitchViewHolder
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InvalidateProfileSwitchTransaction
import info.nightscout.androidaps.databinding.TreatmentsProfileswitchFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsProfileswitchItemBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventEffectiveProfileSwitchChanged
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.extensions.getCustomizedName
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class TreatmentsProfileSwitchFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsProfileswitchFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val disposable = CompositeDisposable()
    private val millsToThePast = T.days(30).msecs()
    private var selectedItems: SparseArray<ProfileSealed> = SparseArray()
    private var showInvalidated = false
    private var removeActionMode: ActionMode? = null
    private var toolbar: Toolbar? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsProfileswitchFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        toolbar = activity?.findViewById(R.id.toolbar)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    private fun refreshFromNightscout() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.refresheventsfromnightscout) + "?") {
                uel.log(Action.TREATMENTS_NS_REFRESH, Sources.Treatments)
                disposable +=
                    Completable.fromAction {
                        repository.deleteAllEffectiveProfileSwitches()
                        repository.deleteAllProfileSwitches()
                    }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error removing entries", it) },
                            onComplete = {
                                rxBus.send(EventProfileSwitchChanged())
                                rxBus.send(EventEffectiveProfileSwitchChanged(0L))
                                rxBus.send(EventNewHistoryData(0, false))
                            }
                        )
                rxBus.send(EventNSClientRestart())
            }
        }
    }

    private fun profileSwitchWithInvalid(now: Long) = repository
        .getProfileSwitchDataIncludingInvalidFromTime(now - millsToThePast, false)
        .map { bolus -> bolus.map { ProfileSealed.PS(it) } }

    private fun effectiveProfileSwitchWithInvalid(now: Long) = repository
        .getEffectiveProfileSwitchDataIncludingInvalidFromTime(now - millsToThePast, false)
        .map { carb -> carb.map { ProfileSealed.EPS(it) } }

    private fun profileSwitches(now: Long) = repository
        .getProfileSwitchDataFromTime(now - millsToThePast, false)
        .map { bolus -> bolus.map { ProfileSealed.PS(it) } }

    private fun effectiveProfileSwitches(now: Long) = repository
        .getEffectiveProfileSwitchDataFromTime(now - millsToThePast, false)
        .map { carb -> carb.map { ProfileSealed.EPS(it) } }

    fun swapAdapter() {
        val now = System.currentTimeMillis()

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
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        removeActionMode?.finish()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    inner class RecyclerProfileViewAdapter(private var profileSwitchList: List<ProfileSealed>) : RecyclerView.Adapter<ProfileSwitchViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ProfileSwitchViewHolder =
            ProfileSwitchViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_profileswitch_item, viewGroup, false))

        override fun onBindViewHolder(holder: ProfileSwitchViewHolder, position: Int) {
            val profileSwitch = profileSwitchList[position]
            holder.binding.ph.visibility = (profileSwitch is ProfileSealed.EPS).toVisibility()
            holder.binding.ns.visibility = (profileSwitch.interfaceIDs_backing?.nightscoutId != null).toVisibility()
            val sameDayPrevious = position > 0 && dateUtil.isSameDay(profileSwitch.timestamp, profileSwitchList[position - 1].timestamp)
            holder.binding.date.visibility = sameDayPrevious.not().toVisibility()
            holder.binding.date.text = dateUtil.dateString(profileSwitch.timestamp)
            holder.binding.time.text = dateUtil.timeString(profileSwitch.timestamp)
            holder.binding.duration.text = rh.gs(R.string.format_mins, T.msecs(profileSwitch.duration ?: 0L).mins())
            holder.binding.name.text =
                if (profileSwitch is ProfileSealed.PS) profileSwitch.value.getCustomizedName() else if (profileSwitch is ProfileSealed.EPS) profileSwitch.value.originalCustomizedName else ""
            if (profileSwitch.isInProgress(dateUtil)) holder.binding.date.setTextColor(rh.gc(R.color.colorActive))
            else holder.binding.date.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.clone.tag = profileSwitch
            holder.binding.name.tag = profileSwitch
            holder.binding.date.tag = profileSwitch
            holder.binding.invalid.visibility = profileSwitch.isValid.not().toVisibility()
            holder.binding.duration.visibility = (profileSwitch.duration != 0L && profileSwitch.duration != null).toVisibility()
            holder.binding.cbRemove.visibility = (removeActionMode != null && profileSwitch is ProfileSealed.PS).toVisibility()
            if (removeActionMode != null) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    if (value) {
                        selectedItems.put(position, profileSwitch)
                    } else {
                        selectedItems.remove(position)
                    }
                    removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
                }
                holder.binding.cbRemove.isChecked = selectedItems.get(position) != null
            }
            holder.binding.clone.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
            holder.binding.spacer.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
            val nextTimestamp = if (profileSwitchList.size != position + 1) profileSwitchList[position + 1].timestamp else 0L
            holder.binding.delimiter.visibility = dateUtil.isSameDay(profileSwitch.timestamp, nextTimestamp).toVisibility()
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
                            rh.gs(R.string.careportal_profileswitch),
                            rh.gs(R.string.copytolocalprofile) + "\n" + profileSwitch.getCustomizedName() + "\n" + dateUtil.dateAndTimeString(profileSwitch.timestamp),
                            Runnable {
                                uel.log(
                                    Action.PROFILE_SWITCH_CLONED, Sources.Treatments,
                                    profileSwitch.getCustomizedName() + " " + dateUtil.dateAndTimeString(profileSwitch.timestamp).replace(".", "_"),
                                    ValueWithUnit.Timestamp(profileSwitch.timestamp),
                                    ValueWithUnit.SimpleString(profileSwitch.profileName)
                                )
                                val nonCustomized = profileSealed.convertToNonCustomizedProfile(dateUtil)
                                localProfilePlugin.addProfile(
                                    localProfilePlugin.copyFrom(
                                        nonCustomized,
                                        profileSwitch.getCustomizedName() + " " + dateUtil.dateAndTimeString(profileSwitch.timestamp).replace(".", "_")
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
                            args.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
                binding.date.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSealed).timestamp)
                            args.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_treatments_profile_switch, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
        val nsUploadOnly = !sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || !buildHelper.isEngineeringMode()
        menu.findItem(R.id.nav_refresh_ns)?.isVisible = !nsUploadOnly

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items -> {
                removeActionMode = toolbar?.startActionMode(RemoveActionModeCallback())
                true
            }

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_refresh_ns -> {
                refreshFromNightscout()
                true
            }

            else -> false
        }

    inner class RemoveActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete_selection, menu)
            selectedItems.clear()
            mode.title = rh.gs(R.string.count_selected, selectedItems.size())
            binding.recyclerview.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove_selected -> {
                    removeSelected()
                    true
                }

                else                 -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            removeActionMode = null
            binding.recyclerview.adapter?.notifyDataSetChanged()
        }
    }

    private fun getConfirmationText(): String {
        if (selectedItems.size() == 1) {
            val profileSwitch = selectedItems.valueAt(0)
            return rh.gs(R.string.careportal_profileswitch) + ": " + profileSwitch.profileName + "\n" + rh.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(profileSwitch.timestamp)
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected() {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                    selectedItems.forEach { _, profileSwitch ->
                        uel.log(
                            Action.PROFILE_SWITCH_REMOVED, Sources.Treatments, profileSwitch.profileName,
                            ValueWithUnit.Timestamp(profileSwitch.timestamp)
                        )
                        disposable += repository.runTransactionForResult(InvalidateProfileSwitchTransaction(profileSwitch.id))
                            .subscribe(
                                { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it") } },
                                { aapsLogger.error(LTag.DATABASE, "Error while invalidating ProfileSwitch", it) }
                            )
                    }
                    removeActionMode?.finish()
                })
            }
        else
            removeActionMode?.finish()
    }
}
