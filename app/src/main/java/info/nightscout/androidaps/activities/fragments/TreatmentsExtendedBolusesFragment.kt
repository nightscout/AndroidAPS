package info.nightscout.androidaps.activities.fragments

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
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.InvalidateExtendedBolusTransaction
import info.nightscout.androidaps.databinding.TreatmentsExtendedbolusFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsExtendedbolusItemBinding
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.isInProgress
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.activities.fragments.TreatmentsExtendedBolusesFragment.RecyclerViewAdapter.ExtendedBolusesViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsExtendedBolusesFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    private val millsToThePast = T.days(30).msecs()

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private var _binding: TreatmentsExtendedbolusFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View =
        TreatmentsExtendedbolusFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        if (binding.showInvalidated.isChecked)
            repository
                .getExtendedBolusDataIncludingInvalidFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
        else
            repository
                .getExtendedBolusDataFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()

        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
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

    private inner class RecyclerViewAdapter(private var extendedBolusList: List<ExtendedBolus>) : RecyclerView.Adapter<ExtendedBolusesViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ExtendedBolusesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_extendedbolus_item, viewGroup, false)
            return ExtendedBolusesViewHolder(v)
        }

        override fun onBindViewHolder(holder: ExtendedBolusesViewHolder, position: Int) {
            val extendedBolus = extendedBolusList[position]
            holder.binding.ns.visibility = (extendedBolus.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.ph.visibility = (extendedBolus.interfaceIDs.pumpId != null).toVisibility()
            holder.binding.invalid.visibility = extendedBolus.isValid.not().toVisibility()
            @SuppressLint("SetTextI18n")
            if (extendedBolus.isInProgress(dateUtil)) {
                holder.binding.date.text = dateUtil.dateAndTimeString(extendedBolus.timestamp)
                holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive))
            } else {
                holder.binding.date.text = dateUtil.dateAndTimeString(extendedBolus.timestamp) + " - " + dateUtil.timeString(extendedBolus.end)
                holder.binding.date.setTextColor(holder.binding.insulin.currentTextColor)
            }
            val profile = profileFunction.getProfile(extendedBolus.timestamp) ?: return
            holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, T.msecs(extendedBolus.duration).mins())
            holder.binding.insulin.text = resourceHelper.gs(R.string.formatinsulinunits, extendedBolus.amount)
            val iob = extendedBolus.iobCalc(System.currentTimeMillis(), profile, activePlugin.activeInsulin)
            holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.iob)
            holder.binding.ratio.text = resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.rate)
            if (iob.iob != 0.0) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.insulin.currentTextColor)
            holder.binding.remove.tag = extendedBolus
        }

        override fun getItemCount(): Int = extendedBolusList.size

        inner class ExtendedBolusesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsExtendedbolusItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val extendedBolus = v.tag as ExtendedBolus
                    context?.let { context ->
                        OKDialog.showConfirmation(context, resourceHelper.gs(R.string.removerecord),
                            """
                ${resourceHelper.gs(R.string.extended_bolus)}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(extendedBolus.timestamp)}
                """.trimIndent(), { _: DialogInterface, _: Int ->
                            uel.log(Action.EXTENDED_BOLUS_REMOVED, Sources.Treatments,
                                ValueWithUnit.Timestamp(extendedBolus.timestamp),
                                ValueWithUnit.Insulin(extendedBolus.amount),
                                ValueWithUnit.UnitPerHour(extendedBolus.rate),
                                ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(extendedBolus.duration).toInt()))
                            disposable += repository.runTransactionForResult(InvalidateExtendedBolusTransaction(extendedBolus.id))
                                .subscribe(
                                    { aapsLogger.debug(LTag.DATABASE, "Removed extended bolus $extendedBolus") },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating extended bolus", it) })
                        }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }
}