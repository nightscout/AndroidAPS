package info.nightscout.androidaps.plugins.treatments.fragments

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.MealLinkLoaded
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.database.transactions.InvalidateBolusTransaction
import info.nightscout.androidaps.database.transactions.InvalidateCarbsTransaction
import info.nightscout.androidaps.database.transactions.InvalidateMealLinkTransaction
import info.nightscout.androidaps.databinding.TreatmentsMealLinkFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsMealLinkItemBinding
import info.nightscout.androidaps.dialogs.WizardInfoDialog
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventTreatmentChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.UploadQueueInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.treatments.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.iobCalc
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

class TreatmentsMealLinkFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueueInterface
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePluginProvider

    private val disposable = CompositeDisposable()

    private val millsToThePast = T.days(30).msecs()

    private var _binding: TreatmentsMealLinkFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsMealLinkFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)

        binding.refreshFromNightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.refresheventsfromnightscout) + "?") {
                    uel.log(Action.TREATMENTS_NS_REFRESH)
                    disposable +=
                        Completable.fromAction {
                            repository.deleteAllMealLinks()
                            repository.deleteAllBoluses()
                            repository.deleteAllCarbs()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .observeOn(aapsSchedulers.main)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error removing entries", it) },
                                onComplete = { rxBus.send(EventTreatmentChange(null)) }
                            )
                    rxBus.send(EventNSClientRestart())
                }
            }
        }
        binding.deleteFutureTreatments.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.overview_treatment_label), resourceHelper.gs(R.string.deletefuturetreatments) + "?", Runnable {
                    uel.log(Action.DELETE_FUTURE_TREATMENTS)
                    repository
                        .getMealLinkLoadedDataFromTime(dateUtil._now(), false)
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list ->
                            list.forEach { mealLinkLoaded ->
                                disposable += repository.runTransactionForResult(InvalidateMealLinkTransaction(mealLinkLoaded.mealLink.id))
                                    .subscribe({
                                        if (mealLinkLoaded.bolus != null) {
                                            val id = mealLinkLoaded.bolus!!.interfaceIDs.nightscoutId
                                            if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                            else uploadQueue.removeByMongoId("dbAdd", mealLinkLoaded.bolus!!.timestamp.toString())
                                        }
                                        if (mealLinkLoaded.carbs != null) {
                                            val id = mealLinkLoaded.carbs!!.interfaceIDs.nightscoutId
                                            if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                            else uploadQueue.removeByMongoId("dbAdd", mealLinkLoaded.carbs!!.timestamp.toString())
                                        }
                                    }, {
                                        aapsLogger.error(LTag.DATATREATMENTS, "Error while invalidating MealLink", it)
                                    })
                            }
                            binding.deleteFutureTreatments.visibility = View.GONE
                        }

                })
            }
        }
        val nsUploadOnly = sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()
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
                    .getMealLinkLoadedDataIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            else
                repository
                    .getMealLinkLoadedDataFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
        disposable += repository
            .getMealLinkLoadedDataFromTime(now, false)
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> binding.deleteFutureTreatments.visibility = list.isNotEmpty().toVisibility() }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(aapsSchedulers.main)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTreatmentUpdateGui::class.java) // TODO join with above
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.main)
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

    inner class RecyclerViewAdapter internal constructor(var mealLinks: List<MealLinkLoaded>) : RecyclerView.Adapter<RecyclerViewAdapter.MealLinkLoadedViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): MealLinkLoadedViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_meal_link_item, viewGroup, false)
            return MealLinkLoadedViewHolder(v)
        }

        override fun onBindViewHolder(holder: MealLinkLoadedViewHolder, position: Int) {
            val profile = profileFunction.getProfile() ?: return
            val ml = mealLinks[position]

            // MealLink
            holder.binding.date.text = dateUtil.dateAndTimeString(ml.mealLink.timestamp)
            val iob = ml.bolus?.iobCalc(activePlugin, System.currentTimeMillis(), profile.dia)
                ?: Iob()
            holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.iobContrib)
            if (iob.iobContrib != 0.0) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.carbs.currentTextColor)
            if (ml.mealLink.timestamp > dateUtil._now()) holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorScheduled)) else holder.binding.date.setTextColor(holder.binding.carbs.currentTextColor)
            holder.binding.mealOrCorrection.text =
                when (ml.bolus?.type) {
                    Bolus.Type.SMB    -> "SMB"
                    Bolus.Type.NORMAL -> resourceHelper.gs(R.string.mealbolus)
                    else              -> ""
                }
            holder.binding.calculation.visibility = (ml.bolusCalculatorResult != null).toVisibility()

            // Bolus
            holder.binding.bolusLayout.visibility = (ml.bolus != null && (ml.bolus?.isValid == true || binding.showInvalidated.isChecked)).toVisibility()
            holder.binding.bolusDate.text = dateUtil.timeString(ml.bolus?.timestamp ?: 0L)
            holder.binding.insulin.text = resourceHelper.gs(R.string.formatinsulinunits, ml.bolus?.amount ?: 0.0)
            holder.binding.bolusNs.visibility = (NSUpload.isIdValid(ml.bolus?.interfaceIDs?.nightscoutId)).toVisibility()
            holder.binding.bolusPump.visibility = (ml.bolus?.interfaceIDs?.pumpId != null).toVisibility()
            holder.binding.bolusInvalid.visibility = (ml.bolus?.isValid == true).not().toVisibility()

            // Carbs
            holder.binding.carbsLayout.visibility = (ml.carbs != null && (ml.carbs?.isValid == true || binding.showInvalidated.isChecked)).toVisibility()
            holder.binding.carbsDate.text = dateUtil.timeString(ml.carbs?.timestamp ?: 0L)
            holder.binding.carbs.text = resourceHelper.gs(R.string.format_carbs, ml.carbs?.amount?.toInt() ?: 0)
            holder.binding.carbsNs.visibility = (NSUpload.isIdValid(ml.carbs?.interfaceIDs?.nightscoutId)).toVisibility()
            holder.binding.carbsPump.visibility = (ml.carbs?.interfaceIDs?.pumpId != null).toVisibility()
            holder.binding.carbsInvalid.visibility = (ml.carbs?.isValid == true).not().toVisibility()

            holder.binding.bolusRemove.visibility = (ml.bolus?.isValid == true).toVisibility()
            holder.binding.carbsRemove.visibility = (ml.carbs?.isValid == true).toVisibility()
            holder.binding.bolusRemove.tag = ml
            holder.binding.carbsRemove.tag = ml
            holder.binding.calculation.tag = ml
        }

        override fun getItemCount(): Int {
            return mealLinks.size
        }

        inner class MealLinkLoadedViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsMealLinkItemBinding.bind(view)

            init {
                binding.calculation.setOnClickListener {
                    val mealLinkLoaded = it.tag as MealLinkLoaded
                    mealLinkLoaded.bolusCalculatorResult?.let { bolusCalculatorResult ->
                        WizardInfoDialog().also { wizardDialog ->
                            wizardDialog.setData(bolusCalculatorResult, mealLinkLoaded.therapyEvent?.note ?: "")
                            wizardDialog.show(childFragmentManager, "WizardInfoDialog")
                        }
                    }
                }
                binding.calculation.paintFlags = binding.calculation.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.bolusRemove.setOnClickListener {
                    val mealLinkLoaded = it.tag as MealLinkLoaded? ?: return@setOnClickListener
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.configbuilder_insulin) + ": " +
                            resourceHelper.gs(R.string.formatinsulinunits, mealLinkLoaded.bolus!!.amount) + "\n" +
                            //         resourceHelper.gs(R.string.carbs) + ": " + resourceHelper.gs(R.string.format_carbs, mealLinkLoaded.carbs.toInt()) + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(mealLinkLoaded.bolus!!.timestamp)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            uel.log(
                                Action.TREATMENT_REMOVED,
                                ValueWithUnit(mealLinkLoaded.bolus!!.timestamp, Units.Timestamp),
                                ValueWithUnit(mealLinkLoaded.bolus!!.amount, Units.U)
                                //              ValueWithUnit(mealLinkLoaded.carbs.toInt(), Units.G)
                            )
                            disposable += repository.runTransactionForResult(InvalidateBolusTransaction(mealLinkLoaded.bolus!!.id))
                                .subscribe({
                                    val id = mealLinkLoaded.bolus!!.interfaceIDs.nightscoutId
                                    if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                    else uploadQueue.removeByMongoId("dbAdd", mealLinkLoaded.bolus!!.timestamp.toString())
                                }, {
                                    aapsLogger.error(LTag.DATATREATMENTS, "Error while invalidating bolus", it)
                                })
                        })
                    }
                }
                binding.bolusRemove.paintFlags = binding.bolusRemove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.carbsRemove.setOnClickListener {
                    val mealLinkLoaded = it.tag as MealLinkLoaded? ?: return@setOnClickListener
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.configbuilder_insulin) + ": " +
                            resourceHelper.gs(R.string.formatinsulinunits, mealLinkLoaded.bolus!!.amount) + "\n" +
                            //         resourceHelper.gs(R.string.carbs) + ": " + resourceHelper.gs(R.string.format_carbs, mealLinkLoaded.carbs.toInt()) + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(mealLinkLoaded.bolus!!.timestamp)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            uel.log(
                                Action.TREATMENT_REMOVED,
                                ValueWithUnit(mealLinkLoaded.bolus!!.timestamp, Units.Timestamp),
                                ValueWithUnit(mealLinkLoaded.bolus!!.amount, Units.U)
                                //              ValueWithUnit(mealLinkLoaded.carbs.toInt(), Units.G)
                            )
                            disposable += repository.runTransactionForResult(InvalidateCarbsTransaction(mealLinkLoaded.carbs!!.id))
                                .subscribe({
                                    val id = mealLinkLoaded.carbs!!.interfaceIDs.nightscoutId
                                    if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                    else uploadQueue.removeByMongoId("dbAdd", mealLinkLoaded.carbs!!.timestamp.toString())
                                }, {
                                    aapsLogger.error(LTag.DATATREATMENTS, "Error while invalidating carbs", it)
                                })
                        })
                    }
                }
                binding.carbsRemove.paintFlags = binding.carbsRemove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}