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
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.events.EventCareportalEventChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsCareportalFragment.RecyclerViewAdapter.CareportalEventsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_careportal_fragment.*
import javax.inject.Inject

class TreatmentsCareportalFragment : DaggerFragment() {
    private val disposable = CompositeDisposable()

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueue
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_careportal_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        careportal_recyclerview.setHasFixedSize(true)
        careportal_recyclerview.layoutManager = LinearLayoutManager(view.context)
        careportal_recyclerview.adapter = RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEvents(false))
        careportal_refreshfromnightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal), resourceHelper.gs(R.string.refresheventsfromnightscout) + " ?", Runnable {
                    MainApp.getDbHelper().resetCareportalEvents()
                    rxBus.send(EventNSClientRestart())
                })
            }
        }
        careportal_removeandroidapsstartedevents.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal), resourceHelper.gs(R.string.careportal_removestartedevents), Runnable {
                    val events = MainApp.getDbHelper().getCareportalEvents(false)
                    for (i in events.indices) {
                        val careportalEvent = events[i]
                        if (careportalEvent.json.contains(resourceHelper.gs(R.string.androidaps_start))) {
                            if (NSUpload.isIdValid(careportalEvent._id))
                                nsUpload.removeCareportalEntryFromNS(careportalEvent._id)
                            else
                                uploadQueue.removeID("dbAdd", careportalEvent._id)
                            MainApp.getDbHelper().delete(careportalEvent)
                        }
                    }
                }, null)
            }
        }

        val nsUploadOnly = sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()
        if (nsUploadOnly) careportal_refreshfromnightscout.visibility = View.GONE
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }) { fabricPrivacy.logException(it) }
        )
        updateGui()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun updateGui() =
        careportal_recyclerview?.swapAdapter(RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEvents(false)), false)

    inner class RecyclerViewAdapter internal constructor(private var careportalEventList: List<CareportalEvent>) : RecyclerView.Adapter<CareportalEventsViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CareportalEventsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_careportal_item, viewGroup, false)
            return CareportalEventsViewHolder(v)
        }

        override fun onBindViewHolder(holder: CareportalEventsViewHolder, position: Int) {
            val careportalEvent = careportalEventList[position]
            holder.ns.visibility = if (NSUpload.isIdValid(careportalEvent._id)) View.VISIBLE else View.GONE
            holder.date.text = dateUtil.dateAndTimeString(careportalEvent.date)
            holder.duration.text = if (careportalEvent.durationInMsec() == 0L) "" else DateUtil.niceTimeScalar(careportalEvent.durationInMsec(), resourceHelper)
            holder.note.text = careportalEvent.notes
            holder.type.text = translator.translate(careportalEvent.eventType)
            holder.remove.tag = careportalEvent
        }

        override fun getItemCount(): Int {
            return careportalEventList.size
        }

        inner class CareportalEventsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var date: TextView = itemView.findViewById(R.id.careportal_date)
            var duration: TextView = itemView.findViewById(R.id.careportal_duration)
            var type: TextView = itemView.findViewById(R.id.careportal_type)
            var note: TextView = itemView.findViewById(R.id.careportal_note)
            var remove: TextView = itemView.findViewById(R.id.careportal_remove)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)

            init {
                remove.setOnClickListener { v: View ->
                    val careportalEvent = v.tag as CareportalEvent
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.eventtype) + ": " + translator.translate(careportalEvent.eventType) + "\n" +
                            resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + careportalEvent.notes + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(careportalEvent.date)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            if (NSUpload.isIdValid(careportalEvent._id))
                                nsUpload.removeCareportalEntryFromNS(careportalEvent._id)
                            else
                                uploadQueue.removeID("dbAdd", careportalEvent._id)
                            MainApp.getDbHelper().delete(careportalEvent)
                        }, null)
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }
}