package info.nightscout.androidaps.plugins.general.food

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InvalidateFoodTransaction
import info.nightscout.androidaps.databinding.FoodFragmentBinding
import info.nightscout.androidaps.databinding.FoodItemBinding
import info.nightscout.androidaps.events.EventFoodDatabaseChanged
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.collections.ArrayList

class FoodFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()
    private var unfiltered: List<Food> = arrayListOf()
    private var filtered: MutableList<Food> = arrayListOf()

    private var _binding: FoodFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FoodFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    @kotlin.ExperimentalStdlibApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)

        binding.refreshFromNightscout.setOnClickListener {
            context?.let { context ->
                OKDialog.showConfirmation(context, resourceHelper.gs(R.string.refresheventsfromnightscout) + " ?", {
                    uel.log(Action.FOOD, Sources.Food, resourceHelper.gs(R.string.refresheventsfromnightscout),
                        ValueWithUnit.SimpleString(resourceHelper.gsNotLocalised(R.string.refresheventsfromnightscout)))
                    disposable += Completable.fromAction { repository.deleteAllFoods() }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error removing foods", it) },
                            onComplete = { rxBus.send(EventFoodDatabaseChanged()) }
                        )

                    rxBus.send(EventNSClientRestart())
                })
            }
        }

        binding.clearfilter.setOnClickListener {
            binding.filter.setText("")
            binding.category.setSelection(0)
            binding.subcategory.setSelection(0)
            filterData()
        }
        binding.category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fillSubcategories()
                filterData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                fillSubcategories()
                filterData()
            }
        }
        binding.subcategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                filterData()
            }
        }
        binding.filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filterData()
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    @Synchronized
    @kotlin.ExperimentalStdlibApi
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventFoodDatabaseChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        )
        swapAdapter()
    }

    @kotlin.ExperimentalStdlibApi
    private fun swapAdapter() {
        disposable += repository
            .getFoodData()
            .observeOn(aapsSchedulers.main)
            .subscribe { list ->
                unfiltered = list
                fillCategories()
                fillSubcategories()
                filterData()
                binding.recyclerview.swapAdapter(RecyclerViewAdapter(filtered), true)
            }
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

    private fun fillCategories() {
        val catSet: MutableSet<CharSequence> = HashSet()
        for (f in unfiltered) {
            val category = f.category
            if (!category.isNullOrBlank()) catSet.add(category)
        }
        // make it unique
        val categories = ArrayList(catSet)
        categories.add(0, resourceHelper.gs(R.string.none))
        context?.let { context ->
            val adapterCategories = ArrayAdapter(context, R.layout.spinner_centered, categories)
            binding.category.adapter = adapterCategories
        }
    }

    private fun fillSubcategories() {
        val categoryFilter = binding.category.selectedItem.toString()
        val subCatSet: MutableSet<CharSequence> = HashSet()
        if (categoryFilter != resourceHelper.gs(R.string.none)) {
            for (f in unfiltered) {
                if (f.category != null && f.category == categoryFilter) {
                    val subCategory = f.subCategory
                    if (!subCategory.isNullOrEmpty()) subCatSet.add(subCategory)
                }
            }
        }
        // make it unique
        val subcategories = ArrayList(subCatSet)
        subcategories.add(0, resourceHelper.gs(R.string.none))
        context?.let { context ->
            val adapterSubcategories = ArrayAdapter(context, R.layout.spinner_centered, subcategories)
            binding.subcategory.adapter = adapterSubcategories
        }
    }

    @kotlin.ExperimentalStdlibApi
    private fun filterData() {
        val textFilter = binding.filter.text.toString()
        val categoryFilter = binding.category.selectedItem?.toString()
            ?: resourceHelper.gs(R.string.none)
        val subcategoryFilter = binding.subcategory.selectedItem?.toString()
            ?: resourceHelper.gs(R.string.none)
        val newFiltered = ArrayList<Food>()
        for (f in unfiltered) {
            if (f.category == null || f.subCategory == null) continue
            if (subcategoryFilter != resourceHelper.gs(R.string.none) && f.subCategory != subcategoryFilter) continue
            if (categoryFilter != resourceHelper.gs(R.string.none) && f.category != categoryFilter) continue
            if (textFilter != "" && !f.name.lowercase(Locale.getDefault()).contains(textFilter.lowercase(Locale.getDefault()))) continue
            newFiltered.add(f)
        }
        filtered = newFiltered
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(filtered), true)
    }

    fun Int?.isNotZero(): Boolean = this != null && this != 0

    inner class RecyclerViewAdapter internal constructor(private var foodList: List<Food>) : RecyclerView.Adapter<RecyclerViewAdapter.FoodsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): FoodsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.food_item, viewGroup, false)
            return FoodsViewHolder(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: FoodsViewHolder, position: Int) {
            val food = foodList[position]
            holder.binding.nsSign.visibility = (food.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.name.text = food.name
            holder.binding.portion.text = food.portion.toString() + food.unit
            holder.binding.carbs.text = food.carbs.toString() + resourceHelper.gs(R.string.shortgramm)
            holder.binding.fat.text = resourceHelper.gs(R.string.shortfat) + ": " + food.fat + resourceHelper.gs(R.string.shortgramm)
            holder.binding.fat.visibility = food.fat.isNotZero().toVisibility()
            holder.binding.protein.text = resourceHelper.gs(R.string.shortprotein) + ": " + food.protein + resourceHelper.gs(R.string.shortgramm)
            holder.binding.protein.visibility = food.protein.isNotZero().toVisibility()
            holder.binding.energy.text = resourceHelper.gs(R.string.shortenergy) + ": " + food.energy + resourceHelper.gs(R.string.shortkilojoul)
            holder.binding.energy.visibility = food.energy.isNotZero().toVisibility()
            holder.binding.remove.tag = food
        }

        override fun getItemCount(): Int = foodList.size

        inner class FoodsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = FoodItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val food = v.tag as Food
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord) + "\n" + food.name, {
                            uel.log(Action.FOOD_REMOVED, Sources.Food, food.name)
                            disposable += repository.runTransactionForResult(InvalidateFoodTransaction(food.id))
                                .subscribe(
                                    { aapsLogger.error(LTag.DATABASE, "Invalidated food $it") },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating food", it) }
                                )
                        }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}