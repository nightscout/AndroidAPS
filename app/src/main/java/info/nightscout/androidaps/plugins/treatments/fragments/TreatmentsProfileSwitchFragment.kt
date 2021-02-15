package info.nightscout.androidaps.plugins.treatments.fragments

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.TreatmentsProfileswitchFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsProfileswitchItemBinding
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsProfileSwitchFragment.RecyclerProfileViewAdapter.ProfileSwitchViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TreatmentsProfileSwitchFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueue
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsProfileswitchFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsProfileswitchFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerProfileViewAdapter(MainApp.getDbHelper().getProfileSwitchData(DateUtil.now() - T.days(30).msecs(), false))

        binding.refreshFromNightscout.setOnClickListener {
            activity?.let { activity ->
                uel.log("PROFILE SWITCH NS REFRESH")
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.refresheventsfromnightscout) + "?") {
                    MainApp.getDbHelper().resetProfileSwitch()
                    rxBus.send(EventNSClientRestart())
                }
            }
        }
        if (sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()) binding.refreshFromNightscout.visibility = View.GONE
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventProfileNeedsUpdate::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        )
        updateGUI()
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

    fun updateGUI() {
        if (_binding == null) return
        binding.recyclerview.swapAdapter(RecyclerProfileViewAdapter(MainApp.getDbHelper().getProfileSwitchData(DateUtil.now() - T.days(30).msecs(), false)), false)
    }

    inner class RecyclerProfileViewAdapter(private var profileSwitchList: List<ProfileSwitch>) : RecyclerView.Adapter<ProfileSwitchViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ProfileSwitchViewHolder =
            ProfileSwitchViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_profileswitch_item, viewGroup, false))

        override fun onBindViewHolder(holder: ProfileSwitchViewHolder, position: Int) {
            val profileSwitch = profileSwitchList[position]
            holder.binding.ph.visibility = (profileSwitch.source == Source.PUMP).toVisibility()
            holder.binding.ns.visibility = NSUpload.isIdValid(profileSwitch._id).toVisibility()
            holder.binding.date.text = dateUtil.dateAndTimeString(profileSwitch.date)
            if (!profileSwitch.isEndingEvent) {
                holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, profileSwitch.durationInMinutes)
            } else {
                holder.binding.duration.text = ""
            }
            holder.binding.name.text = profileSwitch.customizedName
            if (profileSwitch.isInProgress) holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.date.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.remove.tag = profileSwitch
            holder.binding.clone.tag = profileSwitch
            holder.binding.name.tag = profileSwitch
            holder.binding.date.tag = profileSwitch
            holder.binding.invalid.visibility = if (profileSwitch.isValid()) View.GONE else View.VISIBLE
        }

        override fun getItemCount(): Int {
            return profileSwitchList.size
        }

        inner class ProfileSwitchViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsProfileswitchItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener {
                    val profileSwitch = it.tag as ProfileSwitch
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord),
                            resourceHelper.gs(R.string.careportal_profileswitch) + ": " + profileSwitch.profileName +
                                "\n" + resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(profileSwitch.date), Runnable {
                            uel.log("REMOVED PROFILE SWITCH", profileSwitch.profileName + " " + dateUtil.dateAndTimeString(profileSwitch.date))
                            val id = profileSwitch._id
                            if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                            else uploadQueue.removeID("dbAdd", id)
                            MainApp.getDbHelper().delete(profileSwitch)
                        })
                    }
                }
                binding.clone.setOnClickListener {
                    activity?.let { activity ->
                        val profileSwitch = it.tag as ProfileSwitch
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile) + "\n" + profileSwitch.customizedName + "\n" + dateUtil.dateAndTimeString(profileSwitch.date), Runnable {
                            profileSwitch.profileObject?.let {
                                uel.log("PROFILE SWITCH CLONE", profileSwitch.profileName + " " + dateUtil.dateAndTimeString(profileSwitch.date))
                                val nonCustomized = it.convertToNonCustomizedProfile()
                                if (nonCustomized.isValid(resourceHelper.gs(R.string.careportal_profileswitch, false))) {
                                    localProfilePlugin.addProfile(localProfilePlugin.copyFrom(nonCustomized, profileSwitch.customizedName + " " + dateUtil.dateAndTimeString(profileSwitch.date).replace(".", "_")))
                                    rxBus.send(EventLocalProfileChanged())
                                } else {
                                    OKDialog.show(activity, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile_invalid))
                                }
                            }
                        })
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.clone.paintFlags = binding.clone.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.name.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSwitch).date)
                            args.putInt("mode", ProfileViewerDialog.Mode.DB_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
                binding.date.setOnClickListener {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also { args ->
                            args.putLong("time", (it.tag as ProfileSwitch).date)
                            args.putInt("mode", ProfileViewerDialog.Mode.DB_PROFILE.ordinal)
                        }
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
            }
        }
    }
}