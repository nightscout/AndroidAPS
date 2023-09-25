package info.nightscout.ui.activities.fragments

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTreatmentChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.iobCalc
import app.aaps.core.main.utils.ActionModeHelper
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import com.google.gson.Gson
import dagger.android.support.DaggerFragment
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CutCarbsTransaction
import info.nightscout.database.impl.transactions.InvalidateBolusCalculatorResultTransaction
import info.nightscout.database.impl.transactions.InvalidateBolusTransaction
import info.nightscout.database.impl.transactions.InvalidateCarbsTransaction
import info.nightscout.ui.R
import info.nightscout.ui.databinding.TreatmentsBolusCarbsFragmentBinding
import info.nightscout.ui.databinding.TreatmentsBolusCarbsItemBinding
import info.nightscout.ui.dialogs.WizardInfoDialog
import info.nightscout.ui.extensions.isPumpHistory
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsBolusCarbsFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin

    private var _binding: TreatmentsBolusCarbsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null

    class MealLink(
        val bolus: Bolus? = null,
        val carbs: Carbs? = null,
        val bolusCalculatorResult: BolusCalculatorResult? = null
    )

    private val disposable = CompositeDisposable()
    private lateinit var actionHelper: ActionModeHelper<MealLink>
    private val millsToThePast = T.days(30).msecs()
    private var showInvalidated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsBolusCarbsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        actionHelper = ActionModeHelper(rh, activity, this)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
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

    private fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
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
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        actionHelper.finish()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
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

            val newDay = position == 0 || !dateUtil.isSameDayGroup(timestamp(ml), timestamp(mealLinks[position - 1]))
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(timestamp(ml), rh) else ""

            // Metadata
            holder.binding.metadataLayout.visibility = (ml.bolusCalculatorResult != null && (ml.bolusCalculatorResult.isValid || showInvalidated)).toVisibility()
            ml.bolusCalculatorResult?.let { bolusCalculatorResult ->
                holder.binding.calcTime.text = dateUtil.timeString(bolusCalculatorResult.timestamp)
                holder.binding.metadataNs.visibility = (bolusCalculatorResult.interfaceIDs.nightscoutId != null).toVisibility()
                holder.binding.cbMetadataRemove.visibility = (bolusCalculatorResult.isValid && actionHelper.isRemoving).toVisibility()
                if (actionHelper.isRemoving) {
                    holder.binding.cbMetadataRemove.setOnCheckedChangeListener { _, value ->
                        actionHelper.updateSelection(position, ml, value)
                    }
                    holder.binding.root.setOnClickListener {
                        holder.binding.cbMetadataRemove.toggle()
                        actionHelper.updateSelection(position, ml, holder.binding.cbMetadataRemove.isChecked)
                    }
                    holder.binding.cbMetadataRemove.isChecked = actionHelper.isSelected(position)
                }
            }

            // Bolus
            holder.binding.bolusLayout.visibility = (ml.bolus != null && (ml.bolus.isValid || showInvalidated)).toVisibility()
            ml.bolus?.let { bolus ->
                holder.binding.bolusTime.text = dateUtil.timeString(bolus.timestamp)
                holder.binding.insulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolus.amount)
                holder.binding.bolusNs.visibility = (bolus.interfaceIDs.nightscoutId != null).toVisibility()
                holder.binding.bolusPump.visibility = bolus.interfaceIDs.isPumpHistory().toVisibility()
                holder.binding.bolusInvalid.visibility = bolus.isValid.not().toVisibility()
                val iob = bolus.iobCalc(activePlugin, System.currentTimeMillis(), profile.dia)
                if (iob.iobContrib > 0.01) {
                    holder.binding.iob.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.activeColor))
                    holder.binding.iob.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, iob.iobContrib)
                    holder.binding.iobLabel.visibility = View.VISIBLE
                    holder.binding.iob.visibility = View.VISIBLE
                } else {
                    holder.binding.iob.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, 0.0)
                    holder.binding.iob.setTextColor(holder.binding.insulin.currentTextColor)
                    holder.binding.iobLabel.visibility = View.GONE
                    holder.binding.iob.visibility = View.GONE
                }
                if (bolus.timestamp > dateUtil.now())
                    holder.binding.date.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.scheduledColor)) else holder.binding.date.setTextColor(holder.binding.carbs.currentTextColor)
                holder.binding.mealOrCorrection.text =
                    when (ml.bolus.type) {
                        Bolus.Type.SMB     -> "SMB"
                        Bolus.Type.NORMAL  -> rh.gs(R.string.meal_bolus)
                        Bolus.Type.PRIMING -> rh.gs(R.string.prime)
                    }
                holder.binding.cbBolusRemove.visibility = (ml.bolus.isValid && actionHelper.isRemoving).toVisibility()
                if (actionHelper.isRemoving) {
                    holder.binding.cbBolusRemove.setOnCheckedChangeListener { _, value ->
                        actionHelper.updateSelection(position, ml, value)
                    }
                    holder.binding.root.setOnClickListener {
                        holder.binding.cbBolusRemove.toggle()
                        actionHelper.updateSelection(position, ml, holder.binding.cbBolusRemove.isChecked)
                    }
                    holder.binding.cbBolusRemove.isChecked = actionHelper.isSelected(position)
                }
            }
            // Carbs
            holder.binding.carbsLayout.visibility = (ml.carbs != null && (ml.carbs.isValid || showInvalidated)).toVisibility()
            ml.carbs?.let { carbs ->
                holder.binding.carbsTime.text = dateUtil.timeString(carbs.timestamp)
                holder.binding.carbs.text = rh.gs(app.aaps.core.main.R.string.format_carbs, carbs.amount.toInt())
                holder.binding.carbsDuration.text = if (carbs.duration > 0) rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(carbs.duration).mins().toInt()) else ""
                holder.binding.carbsNs.visibility = (carbs.interfaceIDs.nightscoutId != null).toVisibility()
                holder.binding.carbsPump.visibility = carbs.interfaceIDs.isPumpHistory().toVisibility()
                holder.binding.carbsInvalid.visibility = carbs.isValid.not().toVisibility()
                holder.binding.cbCarbsRemove.visibility = (ml.carbs.isValid && actionHelper.isRemoving).toVisibility()
                if (actionHelper.isRemoving) {
                    holder.binding.cbCarbsRemove.setOnCheckedChangeListener { _, value ->
                        actionHelper.updateSelection(position, ml, value)
                    }
                    holder.binding.root.setOnClickListener {
                        holder.binding.cbBolusRemove.toggle()
                        actionHelper.updateSelection(position, ml, holder.binding.cbBolusRemove.isChecked)
                    }
                    holder.binding.cbCarbsRemove.isChecked = actionHelper.isSelected(position)
                }
            }

            holder.binding.calculation.tag = ml

            val notes = ml.carbs?.notes ?: ml.bolus?.notes ?: ""
            holder.binding.notes.text = notes
            holder.binding.notes.visibility = if (notes != "") View.VISIBLE else View.GONE
        }

        override fun getItemCount() = mealLinks.size

        inner class MealLinkLoadedViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsBolusCarbsItemBinding.bind(view)

            init {
                binding.calculation.setOnClickListener {
                    val mealLinkLoaded = it.tag as MealLink? ?: return@setOnClickListener
                    mealLinkLoaded.bolusCalculatorResult?.let { bolusCalculatorResult ->
                        WizardInfoDialog().also { wizardDialog ->
                            wizardDialog.arguments = Bundle().also { bundle ->
                                bundle.putString("data", Gson().toJson(bolusCalculatorResult).toString())
                            }
                            wizardDialog.show(childFragmentManager, "WizardInfoDialog")
                        }
                    }
                }
                binding.calculation.paintFlags = binding.calculation.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_carbs_bolus, menu)
        updateMenuVisibility()
        val hasItems = (binding.recyclerview.adapter?.itemCount ?: 0) > 0
        menu.findItem(R.id.nav_delete_future)?.isVisible = hasItems
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items     -> actionHelper.startRemove()

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.hide_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_delete_future    -> {
                deleteFutureTreatments()
                true
            }

            else                      -> false
        }

    private fun deleteFutureTreatments() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label), rh.gs(app.aaps.core.ui.R.string.delete_future_treatments) + "?", Runnable {
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

    private fun getConfirmationText(selectedItems: SparseArray<MealLink>): String {
        if (selectedItems.size() == 1) {
            val mealLink = selectedItems.valueAt(0)
            val bolus = mealLink.bolus
            if (bolus != null)
                return rh.gs(app.aaps.core.ui.R.string.configbuilder_insulin) + ": " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolus.amount) + "\n" +
                    rh.gs(app.aaps.core.ui.R.string.date) + ": " + dateUtil.dateAndTimeString(bolus.timestamp)
            val carbs = mealLink.carbs
            if (carbs != null)
                return rh.gs(app.aaps.core.ui.R.string.carbs) + ": " + rh.gs(app.aaps.core.main.R.string.format_carbs, carbs.amount.toInt()) + "\n" +
                    rh.gs(app.aaps.core.ui.R.string.date) + ": " + dateUtil.dateAndTimeString(carbs.timestamp)
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<MealLink>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
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
                    ml.bolusCalculatorResult?.let { bolusCalculatorResult ->
                        uel.log(
                            Action.BOLUS_CALCULATOR_RESULT_REMOVED, Sources.Treatments,
                            ValueWithUnit.Timestamp(bolusCalculatorResult.timestamp)
                        )
                        disposable += repository.runTransactionForResult(InvalidateBolusCalculatorResultTransaction(bolusCalculatorResult.id))
                            .subscribe(
                                { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated bolusCalculatorResult $it") } },
                                { aapsLogger.error(LTag.DATABASE, "Error while invalidating bolusCalculatorResult", it) }
                            )
                    }
                }
                actionHelper.finish()
            })
        }
    }

}
