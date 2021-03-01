package info.nightscout.androidaps.plugins.treatments.fragments

import android.annotation.SuppressLint
import android.content.DialogInterface
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
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.InvalidateTemporaryTargetTransaction
import info.nightscout.androidaps.databinding.TreatmentsTemptargetFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsTemptargetItemBinding
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.treatments.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTempTargetFragment.RecyclerViewAdapter.TempTargetsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.friendlyDescription
import info.nightscout.androidaps.utils.extensions.highValueToUnitsToString
import info.nightscout.androidaps.utils.extensions.lowValueToUnitsToString
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsTempTargetFragment : DaggerFragment() {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueue
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    private val millsToThePast = T.days(30).msecs()

    private var _binding: TreatmentsTemptargetFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTemptargetFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.refreshFromNightscout.setOnClickListener {
            context?.let { context ->
                OKDialog.showConfirmation(context, resourceHelper.gs(R.string.refresheventsfromnightscout) + " ?", {
                    uel.log("TT NS REFRESH")
                    disposable += Completable.fromAction { repository.deleteAllTempTargetEntries() }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error removing entries", it) },
                            onComplete = { rxBus.send(EventTempTargetChange()) }
                        )

                    rxBus.send(EventNSClientRestart())
                })
            }
        }
        val nsUploadOnly = sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()
        if (nsUploadOnly) binding.refreshFromNightscout.visibility = View.INVISIBLE
        binding.showInvalidated.setOnCheckedChangeListener { _, _ ->
            rxBus.send(EventTreatmentUpdateGui())
        }
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        if (binding.showInvalidated.isChecked)
            repository
                .getTemporaryTargetDataIncludingInvalidFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
        else
            repository
                .getTemporaryTargetDataFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()

        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
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

    inner class RecyclerViewAdapter internal constructor(private var tempTargetList: List<TemporaryTarget>) : RecyclerView.Adapter<TempTargetsViewHolder>() {

        private val dbRecord = repository.getTemporaryTargetActiveAt(dateUtil._now()).blockingGet()
        private val currentlyActiveTarget = if (dbRecord is ValueWrapper.Existing) dbRecord.value else null

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempTargetsViewHolder =
            TempTargetsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_temptarget_item, viewGroup, false))

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: TempTargetsViewHolder, position: Int) {
            val units = profileFunction.getUnits()
            val tempTarget = tempTargetList[position]
            holder.binding.ns.visibility = (tempTarget.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = tempTarget.isValid.not().toVisibility()
            holder.binding.remove.visibility = tempTarget.isValid.toVisibility()
            holder.binding.date.text = dateUtil.dateAndTimeString(tempTarget.timestamp) + " - " + dateUtil.timeString(tempTarget.end)
            holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, T.msecs(tempTarget.duration).mins())
            holder.binding.low.text = tempTarget.lowValueToUnitsToString(units)
            holder.binding.high.text = tempTarget.highValueToUnitsToString(units)
            holder.binding.reason.text = tempTarget.reason.text
            holder.binding.date.setTextColor(
                when {
                    tempTarget.id == currentlyActiveTarget?.id  -> resourceHelper.gc(R.color.colorActive)
                    tempTarget.timestamp > DateUtil.now() -> resourceHelper.gc(R.color.colorScheduled)
                    else                                  -> holder.binding.reasonColon.currentTextColor
                })
            holder.binding.remove.tag = tempTarget
        }

        override fun getItemCount(): Int = tempTargetList.size

        inner class TempTargetsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsTemptargetItemBinding.bind(view)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val tempTarget = v.tag as TemporaryTarget
                    context?.let { context ->
                        OKDialog.showConfirmation(context, resourceHelper.gs(R.string.removerecord),
                            """
                        ${resourceHelper.gs(R.string.careportal_temporarytarget)}: ${tempTarget.friendlyDescription(profileFunction.getUnits(), resourceHelper)}
                        ${dateUtil.dateAndTimeString(tempTarget.timestamp)}
                        """.trimIndent(),
                            { _: DialogInterface?, _: Int ->
                                uel.log("TT REMOVE", tempTarget.friendlyDescription(profileFunction.getUnits(), resourceHelper))
                                disposable += repository.runTransactionForResult(InvalidateTemporaryTargetTransaction(tempTarget.id))
                                    .subscribe({
                                        val id = tempTarget.interfaceIDs.nightscoutId
                                        if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                        else uploadQueue.removeID("dbAdd", tempTarget.timestamp.toString())
                                    }, {
                                        aapsLogger.error(LTag.BGSOURCE, "Error while invalidating temporary target", it)
                                    })
                            }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}