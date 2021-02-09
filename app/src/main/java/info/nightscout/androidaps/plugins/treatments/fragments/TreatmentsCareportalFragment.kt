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
import info.nightscout.androidaps.databinding.TreatmentsCareportalFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsCareportalItemBinding
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.events.EventCareportalEventChange
import info.nightscout.androidaps.logging.UserEntryLogger
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
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
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
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsCareportalFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsCareportalFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEvents(false))
        binding.refreshFromNightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal), resourceHelper.gs(R.string.refresheventsfromnightscout) + " ?", Runnable {
                    uel.log("CAREPORTAL NS REFRESH")
                    MainApp.getDbHelper().resetCareportalEvents()
                    rxBus.send(EventNSClientRestart())
                })
            }
        }
        binding.removeAndroidapsStartedEvents.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal), resourceHelper.gs(R.string.careportal_removestartedevents), Runnable {
                    uel.log("REMOVED RESTART EVENTS")
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
        if (nsUploadOnly) binding.refreshFromNightscout.visibility = View.GONE
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        )
        updateGui()
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

    private fun updateGui() {
        if (_binding == null) return
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(MainApp.getDbHelper().getCareportalEvents(false)), false)
    }

    inner class RecyclerViewAdapter internal constructor(private var careportalEventList: List<CareportalEvent>) : RecyclerView.Adapter<CareportalEventsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CareportalEventsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_careportal_item, viewGroup, false)
            return CareportalEventsViewHolder(v)
        }

        override fun onBindViewHolder(holder: CareportalEventsViewHolder, position: Int) {
            val careportalEvent = careportalEventList[position]
            holder.binding.ns.visibility = if (NSUpload.isIdValid(careportalEvent._id)) View.VISIBLE else View.GONE
            holder.binding.date.text = dateUtil.dateAndTimeString(careportalEvent.date)
            holder.binding.duration.text = if (careportalEvent.durationInMsec() == 0L) "" else DateUtil.niceTimeScalar(careportalEvent.durationInMsec(), resourceHelper)
            holder.binding.note.text = careportalEvent.notes
            holder.binding.type.text = translator.translate(careportalEvent.eventType)
            holder.binding.remove.tag = careportalEvent
        }

        override fun getItemCount(): Int {
            return careportalEventList.size
        }

        inner class CareportalEventsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsCareportalItemBinding.bind(view)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val careportalEvent = v.tag as CareportalEvent
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.eventtype) + ": " + translator.translate(careportalEvent.eventType) + "\n" +
                            resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + careportalEvent.notes + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(careportalEvent.date)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            uel.log("REMOVED CAREP", text)
                            if (NSUpload.isIdValid(careportalEvent._id))
                                nsUpload.removeCareportalEntryFromNS(careportalEvent._id)
                            else
                                uploadQueue.removeID("dbAdd", careportalEvent._id)
                            MainApp.getDbHelper().delete(careportalEvent)
                        }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }
}