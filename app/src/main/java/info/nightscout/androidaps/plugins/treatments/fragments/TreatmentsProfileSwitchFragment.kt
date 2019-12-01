package info.nightscout.androidaps.plugins.treatments.fragments

import android.content.DialogInterface
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsProfileSwitchFragment.RecyclerProfileViewAdapter.ProfileSwitchViewHolder
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_profileswitch_fragment.*

class TreatmentsProfileSwitchFragment : Fragment() {
    private val disposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_profileswitch_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        profileswitch_recyclerview.setHasFixedSize(true)
        profileswitch_recyclerview.layoutManager = LinearLayoutManager(view.context)
        profileswitch_recyclerview.adapter = RecyclerProfileViewAdapter(MainApp.getDbHelper().getProfileSwitchData(false))

        profileswitch_refreshfromnightscout.setOnClickListener {
            val builder = AlertDialog.Builder(this.context!!)
            builder.setTitle(MainApp.gs(R.string.confirmation))
            builder.setMessage(MainApp.gs(R.string.refresheventsfromnightscout) + "?")
            builder.setPositiveButton(MainApp.gs(R.string.ok)) { dialog: DialogInterface?, id: Int ->
                MainApp.getDbHelper().resetProfileSwitch()
                RxBus.send(EventNSClientRestart())
            }
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null)
            builder.show()
        }
        if (SP.getBoolean(R.string.key_ns_upload_only, false)) profileswitch_refreshfromnightscout.visibility = View.GONE

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventProfileNeedsUpdate::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }) { FabricPrivacy.logException(it) }
        )
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    fun updateGUI() =
            profileswitch_recyclerview?.swapAdapter(RecyclerProfileViewAdapter(MainApp.getDbHelper().getProfileSwitchData(false)), false)

    inner class RecyclerProfileViewAdapter(var profileSwitchList: List<ProfileSwitch>) : RecyclerView.Adapter<ProfileSwitchViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ProfileSwitchViewHolder {
            return ProfileSwitchViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_profileswitch_item, viewGroup, false))
        }

        override fun onBindViewHolder(holder: ProfileSwitchViewHolder, position: Int) {
            val profileSwitch = profileSwitchList[position]
            holder.ph.visibility = if (profileSwitch.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.ns.visibility = if (NSUpload.isIdValid(profileSwitch._id)) View.VISIBLE else View.GONE
            holder.date.text = DateUtil.dateAndTimeString(profileSwitch.date)
            if (!profileSwitch.isEndingEvent) {
                holder.duration.text = DecimalFormatter.to0Decimal(profileSwitch.durationInMinutes.toDouble()) + " " + MainApp.gs(R.string.unit_minute_short)
            } else {
                holder.duration.text = ""
            }
            holder.name.text = profileSwitch.customizedName
            if (profileSwitch.isInProgress) holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive)) else holder.date.setTextColor(holder.duration.currentTextColor)
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
            var cv: CardView = itemView.findViewById<View>(R.id.profileswitch_cardview) as CardView
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
                    R.id.profileswitch_remove ->
                        OKDialog.showConfirmation(activity, MainApp.gs(R.string.removerecord) + "\n" + profileSwitch.profileName + "\n" + DateUtil.dateAndTimeString(profileSwitch.date)) {
                            val id = profileSwitch._id
                            if (NSUpload.isIdValid(id)) NSUpload.removeCareportalEntryFromNS(id)
                            else UploadQueue.removeID("dbAdd", id)
                            MainApp.getDbHelper().delete(profileSwitch)
                        }
                    R.id.profileswitch_clone ->
                        OKDialog.showConfirmation(activity, MainApp.gs(R.string.copytolocalprofile) + "\n" + profileSwitch.customizedName + "\n" + DateUtil.dateAndTimeString(profileSwitch.date)) {
                            profileSwitch.profileObject?.let {
                                val nonCustomized = it.convertToNonCustomizedProfile()
                                LocalProfilePlugin.addProfile(LocalProfilePlugin.SingleProfile().copyFrom(nonCustomized, profileSwitch.customizedName + " " + DateUtil.dateAndTimeString(profileSwitch.date).replace(".", "_")))
                                RxBus.send(EventLocalProfileChanged())
                            }
                        }
                    R.id.profileswitch_date, R.id.profileswitch_name -> {
                        val args = Bundle()
                        args.putLong("time", (v.tag as ProfileSwitch).date)
                        args.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal)
                        val pvd = ProfileViewerDialog()
                        pvd.arguments = args
                        fragmentManager?.let { pvd.show(it, "ProfileViewDialog") }
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