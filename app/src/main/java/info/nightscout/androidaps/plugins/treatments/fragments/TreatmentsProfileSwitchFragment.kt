package info.nightscout.androidaps.plugins.treatments.fragments

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
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
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_profileswitch_fragment.*
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_profileswitch_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        profileswitch_recyclerview.setHasFixedSize(true)
        profileswitch_recyclerview.layoutManager = LinearLayoutManager(view.context)
        profileswitch_recyclerview.adapter = RecyclerProfileViewAdapter(MainApp.getDbHelper().getProfileSwitchData(DateUtil.now() - T.days(30).msecs(), false))

        profileswitch_refreshfromnightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.refresheventsfromnightscout) + "?") {
                    MainApp.getDbHelper().resetProfileSwitch()
                    rxBus.send(EventNSClientRestart())
                }
            }
        }
        if (sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()) profileswitch_refreshfromnightscout.visibility = View.GONE
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventProfileNeedsUpdate::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }) { fabricPrivacy.logException(it) }
        )
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    fun updateGUI() =
        profileswitch_recyclerview?.swapAdapter(RecyclerProfileViewAdapter(MainApp.getDbHelper().getProfileSwitchData(DateUtil.now() - T.days(30).msecs(), false)), false)

    inner class RecyclerProfileViewAdapter(private var profileSwitchList: List<ProfileSwitch>) : RecyclerView.Adapter<ProfileSwitchViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ProfileSwitchViewHolder {
            return ProfileSwitchViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_profileswitch_item, viewGroup, false))
        }

        override fun onBindViewHolder(holder: ProfileSwitchViewHolder, position: Int) {
            val profileSwitch = profileSwitchList[position]
            holder.ph.visibility = (profileSwitch.source == Source.PUMP).toVisibility()
            holder.ns.visibility = NSUpload.isIdValid(profileSwitch._id).toVisibility()
            holder.date.text = dateUtil.dateAndTimeString(profileSwitch.date)
            if (!profileSwitch.isEndingEvent) {
                holder.duration.text = resourceHelper.gs(R.string.format_mins, profileSwitch.durationInMinutes)
            } else {
                holder.duration.text = ""
            }
            holder.name.text = profileSwitch.customizedName
            if (profileSwitch.isInProgress) holder.date.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.date.setTextColor(holder.duration.currentTextColor)
            holder.remove.tag = profileSwitch
            holder.clone.tag = profileSwitch
            holder.name.tag = profileSwitch
            holder.date.tag = profileSwitch
            holder.invalid.visibility = if (profileSwitch.isValid()) View.GONE else View.VISIBLE
        }

        override fun getItemCount(): Int {
            return profileSwitchList.size
        }

        inner class ProfileSwitchViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
            var date: TextView = itemView.findViewById<View>(R.id.profileswitch_date) as TextView
            var duration: TextView = itemView.findViewById<View>(R.id.profileswitch_duration) as TextView
            var name: TextView = itemView.findViewById<View>(R.id.profileswitch_name) as TextView
            var remove: TextView = itemView.findViewById<View>(R.id.profileswitch_remove) as TextView
            var clone: TextView = itemView.findViewById<View>(R.id.profileswitch_clone) as TextView
            var ph: TextView = itemView.findViewById<View>(R.id.pump_sign) as TextView
            var ns: TextView = itemView.findViewById<View>(R.id.ns_sign) as TextView
            var invalid: TextView = itemView.findViewById<View>(R.id.invalid_sign) as TextView

            override fun onClick(v: View) {
                val profileSwitch = v.tag as ProfileSwitch
                when (v.id) {
                    R.id.profileswitch_remove                        ->
                        activity?.let { activity ->
                            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord),
                                resourceHelper.gs(R.string.careportal_profileswitch) + ": " + profileSwitch.profileName +
                                    "\n" + resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(profileSwitch.date), Runnable {
                                val id = profileSwitch._id
                                if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                else uploadQueue.removeID("dbAdd", id)
                                MainApp.getDbHelper().delete(profileSwitch)
                            })
                        }
                    R.id.profileswitch_clone                         ->
                        activity?.let { activity ->
                            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile) + "\n" + profileSwitch.customizedName + "\n" + dateUtil.dateAndTimeString(profileSwitch.date), Runnable {
                                profileSwitch.profileObject?.let {
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

                    R.id.profileswitch_date, R.id.profileswitch_name -> {
                        val args = Bundle()
                        args.putLong("time", (v.tag as ProfileSwitch).date)
                        args.putInt("mode", ProfileViewerDialog.Mode.DB_PROFILE.ordinal)
                        val pvd = ProfileViewerDialog()
                        pvd.arguments = args
                        pvd.show(childFragmentManager, "ProfileViewDialog")
                    }
                }
            }

            init {
                remove.setOnClickListener(this)
                clone.setOnClickListener(this)
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                clone.paintFlags = clone.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                name.setOnClickListener(this)
                date.setOnClickListener(this)
            }
        }
    }
}