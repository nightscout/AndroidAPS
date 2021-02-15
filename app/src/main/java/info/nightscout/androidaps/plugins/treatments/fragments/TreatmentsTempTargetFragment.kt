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
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.databinding.TreatmentsTemptargetFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsTemptargetItemBinding
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTempTargetFragment.RecyclerViewAdapter.TempTargetsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TreatmentsTempTargetFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueue
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    private var _binding: TreatmentsTemptargetFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTemptargetFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerViewAdapter(activePlugin.activeTreatments.tempTargetsFromHistory)
        binding.refreshFromNightscout.setOnClickListener {
            context?.let { context ->
                OKDialog.showConfirmation(context, resourceHelper.gs(R.string.refresheventsfromnightscout) + " ?", {
                    uel.log("TT NS REFRESH")
                    MainApp.getDbHelper().resetTempTargets()
                    rxBus.send(EventNSClientRestart())
                })
            }
        }
        val nsUploadOnly = sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()
        if (nsUploadOnly) binding.refreshFromNightscout.visibility = View.GONE
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventTempTargetChange::class.java)
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
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(activePlugin.activeTreatments.tempTargetsFromHistory), false)
    }

    inner class RecyclerViewAdapter internal constructor(var tempTargetList: Intervals<TempTarget>) : RecyclerView.Adapter<TempTargetsViewHolder>() {

        var currentlyActiveTarget: TempTarget? = tempTargetList.getValueByInterval(System.currentTimeMillis())

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempTargetsViewHolder =
            TempTargetsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_temptarget_item, viewGroup, false))

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: TempTargetsViewHolder, position: Int) {
            val units = profileFunction.getUnits()
            val tempTarget = tempTargetList.getReversed(position)
            holder.binding.ph.visibility = if (tempTarget.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.binding.ns.visibility = if (NSUpload.isIdValid(tempTarget._id)) View.VISIBLE else View.GONE
            if (!tempTarget.isEndingEvent) {
                holder.binding.date.text = dateUtil.dateAndTimeString(tempTarget.date) + " - " + dateUtil.timeString(tempTarget.originalEnd())
                holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, tempTarget.durationInMinutes)
                holder.binding.low.text = tempTarget.lowValueToUnitsToString(units)
                holder.binding.high.text = tempTarget.highValueToUnitsToString(units)
                holder.binding.reason.text = tempTarget.reason
            } else {
                holder.binding.date.text = dateUtil.dateAndTimeString(tempTarget.date)
                holder.binding.duration.setText(R.string.cancel)
                holder.binding.low.text = ""
                holder.binding.high.text = ""
                holder.binding.reason.text = ""
                holder.binding.reasonLabel.text = ""
                holder.binding.reasonColon.text = ""
            }
            if (tempTarget.isInProgress && tempTarget === currentlyActiveTarget) {
                holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive))
            } else if (tempTarget.date > DateUtil.now()) {
                holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorScheduled))
            } else {
                holder.binding.date.setTextColor(holder.binding.reasonColon.currentTextColor)
            }
            holder.binding.remove.tag = tempTarget
        }

        override fun getItemCount(): Int = tempTargetList.size()

        inner class TempTargetsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsTemptargetItemBinding.bind(view)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val tempTarget = v.tag as TempTarget
                    context?.let { context ->
                        OKDialog.showConfirmation(context, resourceHelper.gs(R.string.removerecord),
                            """
                        ${resourceHelper.gs(R.string.careportal_temporarytarget)}: ${tempTarget.friendlyDescription(profileFunction.getUnits(), resourceHelper)}
                        ${dateUtil.dateAndTimeString(tempTarget.date)}
                        """.trimIndent(),
                            { _: DialogInterface?, _: Int ->
                                uel.log("TT REMOVE", tempTarget.friendlyDescription(profileFunction.getUnits(), resourceHelper))
                                val id = tempTarget._id
                                if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                else uploadQueue.removeID("dbAdd", id)

                                MainApp.getDbHelper().delete(tempTarget)
                            }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}