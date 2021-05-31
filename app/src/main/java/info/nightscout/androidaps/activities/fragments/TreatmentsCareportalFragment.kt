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
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.transactions.InvalidateAAPSStartedTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.InvalidateTherapyEventTransaction
import info.nightscout.androidaps.databinding.TreatmentsCareportalFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsCareportalItemBinding
import info.nightscout.androidaps.events.EventTherapyEventChange
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.activities.fragments.TreatmentsCareportalFragment.RecyclerViewAdapter.TherapyEventsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsCareportalFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    private val millsToThePast = T.days(30).msecs()

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
        binding.refreshFromNightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal), resourceHelper.gs(R.string.refresheventsfromnightscout) + " ?", Runnable {
                    uel.log(Action.CAREPORTAL_NS_REFRESH, Sources.Treatments)
                    disposable += Completable.fromAction { repository.deleteAllTherapyEventsEntries() }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error removing entries", it) },
                            onComplete = { rxBus.send(EventTherapyEventChange()) }
                        )
                    rxBus.send(EventNSClientRestart())
                })
            }
        }
        binding.removeAndroidapsStartedEvents.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal), resourceHelper.gs(R.string.careportal_removestartedevents), Runnable {
                    uel.log(Action.RESTART_EVENTS_REMOVED, Sources.Treatments)
                    repository.runTransactionForResult(InvalidateAAPSStartedTherapyEventTransaction(resourceHelper.gs(R.string.androidaps_start)))
                        .subscribe(
                            { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated therapy event $it") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while invalidating therapy event", it) }
                        )
                }, null)
            }
        }

        val nsUploadOnly = !sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || !buildHelper.isEngineeringMode()
        if (nsUploadOnly) binding.refreshFromNightscout.visibility = View.GONE
        binding.showInvalidated.setOnCheckedChangeListener { _, _ ->
            rxBus.send(EventTreatmentUpdateGui())
        }
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        disposable +=
            if (binding.showInvalidated.isChecked)
                repository
                    .getTherapyEventDataIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            else
                repository
                    .getTherapyEventDataFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.main)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTreatmentUpdateGui::class.java) // TODO join with above
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
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
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    inner class RecyclerViewAdapter internal constructor(private var list: List<TherapyEvent>) : RecyclerView.Adapter<TherapyEventsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TherapyEventsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_careportal_item, viewGroup, false)
            return TherapyEventsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TherapyEventsViewHolder, position: Int) {
            val therapyEvent = list[position]
            holder.binding.ns.visibility = (therapyEvent.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = therapyEvent.isValid.not().toVisibility()
            holder.binding.date.text = dateUtil.dateAndTimeString(therapyEvent.timestamp)
            holder.binding.duration.text = if (therapyEvent.duration == 0L) "" else dateUtil.niceTimeScalar(therapyEvent.duration, resourceHelper)
            holder.binding.note.text = therapyEvent.note
            holder.binding.type.text = translator.translate(therapyEvent.type)
            holder.binding.remove.tag = therapyEvent
        }

        override fun getItemCount(): Int {
            return list.size
        }

        inner class TherapyEventsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsCareportalItemBinding.bind(view)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val therapyEvent = v.tag as TherapyEvent
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.eventtype) + ": " + translator.translate(therapyEvent.type) + "\n" +
                            resourceHelper.gs(R.string.notes_label) + ": " + (therapyEvent.note
                            ?: "") + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(therapyEvent.timestamp)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            uel.log(Action.CAREPORTAL_REMOVED, Sources.Treatments, therapyEvent.note ,
                                ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                ValueWithUnit.TherapyEventType(therapyEvent.type))
                            disposable += repository.runTransactionForResult(InvalidateTherapyEventTransaction(therapyEvent.id))
                                .subscribe(
                                    { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated therapy event $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating therapy event", it) }
                                )
                        }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }
}