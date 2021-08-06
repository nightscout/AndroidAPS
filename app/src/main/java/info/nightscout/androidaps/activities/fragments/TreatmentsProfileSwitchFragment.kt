package info.nightscout.androidaps.activities.fragments

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InvalidateProfileSwitchTransaction
import info.nightscout.androidaps.databinding.TreatmentsProfileswitchFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsProfileswitchItemBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.extensions.getCustomizedName
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.activities.fragments.TreatmentsProfileSwitchFragment.RecyclerProfileViewAdapter.ProfileSwitchViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

class TreatmentsProfileSwitchFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsProfileswitchFragmentBinding? = null

    private val disposable = CompositeDisposable()

    private val millsToThePast = T.days(30).msecs()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsProfileswitchFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)

        binding.refreshFromNightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.refresheventsfromnightscout) + "?") {
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
                                    rxBus.send(EventNewHistoryData(0, false))
                                }
                            )
                    rxBus.send(EventNSClientRestart())
                }
            }
        }
        if (!sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || !buildHelper.isEngineeringMode()) binding.refreshFromNightscout.visibility = View.GONE
        binding.showInvalidated.setOnCheckedChangeListener { _, _ ->
            rxBus.send(EventTreatmentUpdateGui())
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

        if (binding.showInvalidated.isChecked)
            disposable += profileSwitchWithInvalid(now)
                .zipWith(effectiveProfileSwitchWithInvalid(now)) { first, second -> first + second }
                .map { ml -> ml.sortedByDescending { it.timestamp } }
                .observeOn(aapsSchedulers.main)
                .subscribe { list ->
                    binding.recyclerview.swapAdapter(RecyclerProfileViewAdapter(list), true)
                }
        else
            disposable += profileSwitches(now)
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
        disposable.add(rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        )
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
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
            holder.binding.ns.visibility = (profileSwitch.interfaceIDs_backing?.nightscoutId != null).toVisibility()
            holder.binding.date.text = dateUtil.dateAndTimeString(profileSwitch.timestamp)
            holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, T.msecs(profileSwitch.duration ?: 0L).mins())
            holder.binding.name.text = if (profileSwitch is ProfileSealed.PS) profileSwitch.value.getCustomizedName() else if (profileSwitch is ProfileSealed.EPS) profileSwitch.value.originalCustomizedName else ""
            if (profileSwitch.isInProgress(dateUtil)) holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive))
            else holder.binding.date.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.remove.tag = profileSwitch
            holder.binding.clone.tag = profileSwitch
            holder.binding.name.tag = profileSwitch
            holder.binding.date.tag = profileSwitch
            holder.binding.invalid.visibility = profileSwitch.isValid.not().toVisibility()
            holder.binding.duration.visibility = (profileSwitch.duration != 0L && profileSwitch.duration != null).toVisibility()
            holder.binding.remove.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
            holder.binding.clone.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
            holder.binding.spacer.visibility = (profileSwitch is ProfileSealed.PS).toVisibility()
            holder.binding.root.setBackgroundColor(resourceHelper.gc(if (profileSwitch is ProfileSealed.PS) R.color.defaultbackground else R.color.list_delimiter))
        }

        override fun getItemCount(): Int {
            return profileSwitchList.size
        }

        inner class ProfileSwitchViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsProfileswitchItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { view ->
                    val profileSwitch = view.tag as ProfileSealed.PS
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord),
                            resourceHelper.gs(R.string.careportal_profileswitch) + ": " + profileSwitch.profileName +
                                "\n" + resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(profileSwitch.timestamp), Runnable {
                            uel.log(Action.PROFILE_SWITCH_REMOVED, Sources.Treatments, profileSwitch.profileName,
                                ValueWithUnit.Timestamp(profileSwitch.timestamp))
                            disposable += repository.runTransactionForResult(InvalidateProfileSwitchTransaction(profileSwitch.id))
                                .subscribe(
                                    { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating ProfileSwitch", it) }
                                )
                        })
                    }
                }
                binding.clone.setOnClickListener {
                    activity?.let { activity ->
                        val profileSwitch = (it.tag as ProfileSealed.PS).value
                        val profileSealed = it.tag as ProfileSealed
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile) + "\n" + profileSwitch.getCustomizedName() + "\n" + dateUtil.dateAndTimeString(profileSwitch.timestamp), Runnable {
                            uel.log(Action.PROFILE_SWITCH_CLONED, Sources.Treatments,
                                profileSwitch.getCustomizedName() + " " + dateUtil.dateAndTimeString(profileSwitch.timestamp).replace(".", "_"),
                                ValueWithUnit.Timestamp(profileSwitch.timestamp),
                                ValueWithUnit.SimpleString(profileSwitch.profileName))
                            val nonCustomized = profileSealed.convertToNonCustomizedProfile(dateUtil)
                            localProfilePlugin.addProfile(localProfilePlugin.copyFrom(nonCustomized, profileSwitch.getCustomizedName() + " " + dateUtil.dateAndTimeString(profileSwitch.timestamp).replace(".", "_")))
                            rxBus.send(EventLocalProfileChanged())
                        })
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.clone.paintFlags = binding.clone.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.name.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSealed).timestamp)
                            args.putInt("mode", ProfileViewerDialog.Mode.DB_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
                binding.date.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSealed).timestamp)
                            args.putInt("mode", ProfileViewerDialog.Mode.DB_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
            }
        }
    }
}