package info.nightscout.androidaps.activities.fragments

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.widget.CompoundButton
import android.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.util.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.CutCarbsTransaction
import info.nightscout.androidaps.database.transactions.InvalidateBolusCalculatorResultTransaction
import info.nightscout.androidaps.database.transactions.InvalidateBolusTransaction
import info.nightscout.androidaps.database.transactions.InvalidateCarbsTransaction
import info.nightscout.androidaps.databinding.TreatmentsBolusCarbsFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsBolusCarbsItemBinding
import info.nightscout.androidaps.dialogs.WizardInfoDialog
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventTreatmentChange
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsBolusCarbsFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin

    private var _binding: TreatmentsBolusCarbsFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    class MealLink(
        val bolus: Bolus? = null,
        val carbs: Carbs? = null,
        val bolusCalculatorResult: BolusCalculatorResult? = null
    )

    private val disposable = CompositeDisposable()
    private val millsToThePast = T.days(30).msecs()

    private var selectedItems: SparseArray<MealLink> = SparseArray()
    private var showInvalidated = false
    private var removeActionMode: ActionMode? = null
    private var toolbar: Toolbar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsBolusCarbsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("CheckResult")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        toolbar = activity?.findViewById(R.id.toolbar)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    private fun bolusMealLinksWithInvalid(now: Long) = repository
        .getBolusesIncludingInvalidFromTime(now - millsToThePast, false)
        .map { bolus -> bolus.map { MealLink(bolus = it) } }

    private fun carbsMealLinksWithInvalid(now: Long) = repository
        .getCarbsIncludingInvalidFromTime(now - millsToThePast, false)
        .map { carb -> carb.map { MealLink(carbs = it) } }

    private fun calcResultMealLinksWithInvalid(now: Long) = repository
        .getBolusCalculatorResultsIncludingInvalidFromTime(now - millsToThePast, false)
        .map { calc -> calc.map { MealLink(bolusCalculatorResult = it) } }

    private fun bolusMealLinks(now: Long) = repository
        .getBolusesDataFromTime(now - millsToThePast, false)
        .map { bolus -> bolus.map { MealLink(bolus = it) } }

    private fun carbsMealLinks(now: Long) = repository
        .getCarbsDataFromTime(now - millsToThePast, false)
        .map { carb -> carb.map { MealLink(carbs = it) } }

    private fun calcResultMealLinks(now: Long) = repository
        .getBolusCalculatorResultsDataFromTime(now - millsToThePast, false)
        .map { calc -> calc.map { MealLink(bolusCalculatorResult = it) } }

    fun swapAdapter() {
        val now = System.currentTimeMillis()

        disposable += 
            if (showInvalidated)
                carbsMealLinksWithInvalid(now)
                    .zipWith(bolusMealLinksWithInvalid(now)) { first, second -> first + second }
                    .zipWith(calcResultMealLinksWithInvalid(now)) { first, second -> first + second }
                    .map { ml ->
                        ml.sortedByDescending {
                            it.carbs?.timestamp ?: it.bolus?.timestamp
                            ?: it.bolusCalculatorResult?.timestamp
                        }
                    }
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true)
                    }
            else
                carbsMealLinks(now)
                    .zipWith(bolusMealLinks(now)) { first, second -> first + second }
                    .zipWith(calcResultMealLinks(now)) { first, second -> first + second }
                    .map { ml ->
                        ml.sortedByDescending {
                            it.carbs?.timestamp ?: it.bolus?.timestamp
                            ?: it.bolusCalculatorResult?.timestamp
                        }
                    }
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true)
                    }

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
        removeActionMode?.let { it.finish() }
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    private fun timestamp(ml: MealLink): Long = ml.bolusCalculatorResult?.timestamp ?: ml.bolus?.timestamp ?: ml.carbs?.timestamp ?: 0L

    inner class RecyclerViewAdapter internal constructor(private var mealLinks: List<MealLink>) : RecyclerView.Adapter<RecyclerViewAdapter.MealLinkLoadedViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): MealLinkLoadedViewHolder =
            MealLinkLoadedViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_bolus_carbs_item, viewGroup, false))

        override fun onBindViewHolder(holder: MealLinkLoadedViewHolder, position: Int) {
            val profile = profileFunction.getProfile() ?: return
            val ml = mealLinks[position]

            val sameDayPrevious = position > 0 && dateUtil.isSameDay(timestamp(ml), timestamp(mealLinks[position - 1]))
            holder.binding.date.visibility = sameDayPrevious.not().toVisibility()
            holder.binding.date.text = dateUtil.dateString(timestamp(ml))

            // Metadata
            holder.binding.metadataLayout.visibility = (ml.bolusCalculatorResult != null && (ml.bolusCalculatorResult.isValid || showInvalidated)).toVisibility()
            ml.bolusCalculatorResult?.let { bolusCalculatorResult ->
                holder.binding.calcTime.text = dateUtil.timeString(bolusCalculatorResult.timestamp)
            }

            // Bolus
            holder.binding.bolusLayout.visibility = (ml.bolus != null && (ml.bolus.isValid || showInvalidated)).toVisibility()
            ml.bolus?.let { bolus ->
                holder.binding.bolusTime.text = dateUtil.timeString(bolus.timestamp)
                holder.binding.insulin.text = rh.gs(R.string.formatinsulinunits, bolus.amount)
                holder.binding.bolusNs.visibility = (bolus.interfaceIDs.nightscoutId != null).toVisibility()
                holder.binding.bolusPump.visibility = (bolus.interfaceIDs.pumpId != null).toVisibility()
                holder.binding.bolusInvalid.visibility = bolus.isValid.not().toVisibility()
                val iob = bolus.iobCalc(activePlugin, System.currentTimeMillis(), profile.dia)
                if (iob.iobContrib > 0.01) {
                    holder.binding.iob.setTextColor(rh.gc(R.color.colorActive))
                    holder.binding.iob.text = rh.gs(R.string.formatinsulinunits, iob.iobContrib)
                    holder.binding.iobLabel.visibility = View.VISIBLE
                    holder.binding.iob.visibility = View.VISIBLE
                } else {
                    holder.binding.iob.text = rh.gs(R.string.formatinsulinunits, 0.0)
                    holder.binding.iob.setTextColor(holder.binding.insulin.currentTextColor)
                    holder.binding.iobLabel.visibility = View.GONE
                    holder.binding.iob.visibility = View.GONE
                }
                if (bolus.timestamp > dateUtil.now()) holder.binding.date.setTextColor(rh.gc(R.color.colorScheduled)) else holder.binding.date.setTextColor(holder.binding.carbs.currentTextColor)
                holder.binding.mealOrCorrection.text =
                    when (ml.bolus.type) {
                        Bolus.Type.SMB     -> "SMB"
                        Bolus.Type.NORMAL  -> rh.gs(R.string.mealbolus)
                        Bolus.Type.PRIMING -> rh.gs(R.string.prime)
                    }
            }
            // Carbs
            holder.binding.carbsLayout.visibility = (ml.carbs != null && (ml.carbs.isValid || showInvalidated)).toVisibility()
            ml.carbs?.let { carbs ->
                holder.binding.carbsTime.text = dateUtil.timeString(carbs.timestamp)
                holder.binding.carbs.text = rh.gs(R.string.format_carbs, carbs.amount.toInt())
                holder.binding.carbsDuration.text = if (carbs.duration > 0) rh.gs(R.string.format_mins, T.msecs(carbs.duration).mins().toInt()) else ""
                holder.binding.carbsNs.visibility = (carbs.interfaceIDs.nightscoutId != null).toVisibility()
                holder.binding.carbsPump.visibility = (carbs.interfaceIDs.pumpId != null).toVisibility()
                holder.binding.carbsInvalid.visibility = carbs.isValid.not().toVisibility()
            }
            holder.binding.cbBolusRemove.visibility = (ml.bolus?.isValid == true && removeActionMode != null).toVisibility()
            holder.binding.cbCarbsRemove.visibility = (ml.carbs?.isValid == true && removeActionMode != null).toVisibility()
            if (removeActionMode != null) {
                val onChange = CompoundButton.OnCheckedChangeListener { _, value ->
                    if (value) {
                        selectedItems.put(position, ml)
                    } else {
                        selectedItems.remove(position)
                    }
                    removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
                }
                holder.binding.cbBolusRemove.setOnCheckedChangeListener(onChange)
                holder.binding.cbBolusRemove.isChecked = selectedItems.get(position) != null
                holder.binding.cbCarbsRemove.setOnCheckedChangeListener(onChange)
                holder.binding.cbCarbsRemove.isChecked = selectedItems.get(position) != null
            }
            holder.binding.calculation.tag = ml
            val nextTimestamp = if (mealLinks.size != position + 1) timestamp(mealLinks[position + 1]) else 0L
            holder.binding.delimiter.visibility = dateUtil.isSameDay(timestamp(ml), nextTimestamp).toVisibility()
        }

        override fun getItemCount() = mealLinks.size

        inner class MealLinkLoadedViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsBolusCarbsItemBinding.bind(view)

            init {
                binding.calculation.setOnClickListener {
                    val mealLinkLoaded = it.tag as MealLink? ?: return@setOnClickListener
                    mealLinkLoaded.bolusCalculatorResult?.let { bolusCalculatorResult ->
                        WizardInfoDialog().also { wizardDialog ->
                            wizardDialog.setData(bolusCalculatorResult)
                            wizardDialog.show(childFragmentManager, "WizardInfoDialog")
                        }
                    }
                }
                binding.calculation.paintFlags = binding.calculation.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_treatments_carbs_bolus, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
        val nsUploadOnly = !sp.getBoolean(R.string.key_ns_receive_insulin, false) || !sp.getBoolean(R.string.key_ns_receive_carbs, false) || !buildHelper.isEngineeringMode()
        menu.findItem(R.id.nav_refresh_ns)?.isVisible = !nsUploadOnly
        val hasItems = (binding.recyclerview.adapter?.itemCount ?: 0) > 0
        menu.findItem(R.id.nav_delete_future)?.isVisible = hasItems

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items     -> {
                removeActionMode = toolbar?.startActionMode(RemoveActionModeCallback())
                true
            }

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_delete_future    -> {
                deleteFutureTreatments()
                true
            }

            R.id.nav_refresh_ns       -> {
                refreshFromNightscout()
                true
            }

            else                      -> false
        }

    private fun refreshFromNightscout() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.refresheventsfromnightscout) + "?") {
                uel.log(Action.TREATMENTS_NS_REFRESH, Sources.Treatments)
                disposable +=
                    Completable.fromAction {
                        repository.deleteAllBolusCalculatorResults()
                        repository.deleteAllBoluses()
                        repository.deleteAllCarbs()
                    }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error removing entries", it) },
                            onComplete = {
                                rxBus.send(EventTreatmentChange())
                                rxBus.send(EventNewHistoryData(0, false))
                            }
                        )
                rxBus.send(EventNSClientRestart())
            }
        }
    }

    fun deleteFutureTreatments() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.overview_treatment_label), rh.gs(R.string.deletefuturetreatments) + "?", Runnable {
                uel.log(Action.DELETE_FUTURE_TREATMENTS, Sources.Treatments)
                disposable += repository
                    .getBolusesDataFromTime(dateUtil.now(), false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        list.forEach { bolus ->
                            disposable += repository.runTransactionForResult(InvalidateBolusTransaction(bolus.id))
                                .subscribe(
                                    { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating bolus", it) }
                                )
                        }
                    }
                disposable += repository
                    .getCarbsDataFromTimeNotExpanded(dateUtil.now(), false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        list.forEach { carb ->
                            if (carb.duration == 0L)
                                disposable += repository.runTransactionForResult(InvalidateCarbsTransaction(carb.id))
                                    .subscribe(
                                        { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it") } },
                                        { aapsLogger.error(LTag.DATABASE, "Error while invalidating carbs", it) }
                                    )
                            else {
                                disposable += repository.runTransactionForResult(CutCarbsTransaction(carb.id, dateUtil.now()))
                                    .subscribe(
                                        { result ->
                                            result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it") }
                                            result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated (cut end) carbs $it") }
                                        },
                                        { aapsLogger.error(LTag.DATABASE, "Error while invalidating carbs", it) }
                                    )
                            }
                        }
                    }
                disposable += repository
                    .getBolusCalculatorResultsDataFromTime(dateUtil.now(), false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        list.forEach { bolusCalc ->
                            disposable += repository.runTransactionForResult(InvalidateBolusCalculatorResultTransaction(bolusCalc.id))
                                .subscribe(
                                    { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated bolusCalculatorResult $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating bolusCalculatorResult", it) }
                                )
                        }
                    }
            })
        }
    }

    inner class RemoveActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete_selection, menu)
            selectedItems.clear()
            mode.title = rh.gs(R.string.count_selected, selectedItems.size())
            binding.recyclerview.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove_selected -> {
                    removeSelected()
                    true
                }

                else                 -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            removeActionMode = null
            binding.recyclerview.adapter?.notifyDataSetChanged()
        }
    }

    private fun getConfirmationText(): String {
        if (selectedItems.size() == 1) {
            val mealLink = selectedItems.valueAt(0)
            val bolus = mealLink.bolus
            if (bolus != null)
                return rh.gs(R.string.configbuilder_insulin) + ": " + rh.gs(R.string.formatinsulinunits, bolus.amount) + "\n" +
                    rh.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(bolus.timestamp)
            val carbs = mealLink.carbs
            if (carbs != null)
                return rh.gs(R.string.carbs) + ": " + rh.gs(R.string.format_carbs, carbs.amount.toInt()) + "\n" +
                    rh.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(carbs.timestamp)
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    fun removeSelected() {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                    selectedItems.forEach { _, ml ->
                        ml.bolus?.let { bolus ->
                            uel.log(
                                Action.BOLUS_REMOVED, Sources.Treatments,
                                ValueWithUnit.Timestamp(bolus.timestamp),
                                ValueWithUnit.Insulin(bolus.amount)
                            )
                            disposable += repository.runTransactionForResult(InvalidateBolusTransaction(bolus.id))
                                .subscribe(
                                    { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating bolus", it) }
                                )
                        }
                        ml.carbs?.let { carb ->
                            uel.log(
                                Action.CARBS_REMOVED, Sources.Treatments,
                                ValueWithUnit.Timestamp(carb.timestamp),
                                ValueWithUnit.Gram(carb.amount.toInt())
                            )
                            disposable += repository.runTransactionForResult(InvalidateCarbsTransaction(carb.id))
                                .subscribe(
                                    { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it") } },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating carbs", it) }
                                )
                        }
                    }
                    removeActionMode?.finish()
                })
            }
        else
            removeActionMode?.finish()
    }
}
