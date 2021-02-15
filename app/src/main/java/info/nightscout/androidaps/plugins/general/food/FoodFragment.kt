package info.nightscout.androidaps.plugins.general.food

import android.annotation.SuppressLint
import android.content.DialogInterface
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
import info.nightscout.androidaps.databinding.FoodFragmentBinding
import info.nightscout.androidaps.databinding.FoodItemBinding
import info.nightscout.androidaps.events.EventFoodDatabaseChanged
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.food.FoodFragment.RecyclerViewAdapter.FoodsViewHolder
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class FoodFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var foodPlugin: FoodPlugin
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()
    private lateinit var unfiltered: List<Food>
    private lateinit var filtered: MutableList<Food>

    private var _binding: FoodFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FoodFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerViewAdapter(foodPlugin.service?.foodData
            ?: ArrayList())

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
        loadData()
        fillCategories()
        fillSubcategories()
        filterData()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventFoodDatabaseChanged::class.java)
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

    private fun loadData() {
        unfiltered = foodPlugin.service?.foodData ?: ArrayList()
    }

    private fun fillCategories() {
        val catSet: MutableSet<CharSequence> = HashSet()
        for (f in unfiltered) {
            if (f.category != null && f.category != "") catSet.add(f.category)
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
                if (f.category != null && f.category == categoryFilter) if (f.subcategory != null && f.subcategory != "") subCatSet.add(f.subcategory)
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

    private fun filterData() {
        val textFilter = binding.filter.text.toString()
        val categoryFilter = binding.category.selectedItem.toString()
        val subcategoryFilter = binding.subcategory.selectedItem.toString()
        val newFiltered = ArrayList<Food>()
        for (f in unfiltered) {
            if (f.name == null || f.category == null || f.subcategory == null) continue
            if (subcategoryFilter != resourceHelper.gs(R.string.none) && f.subcategory != subcategoryFilter) continue
            if (categoryFilter != resourceHelper.gs(R.string.none) && f.category != categoryFilter) continue
            if (textFilter != "" && !f.name.toLowerCase(Locale.getDefault()).contains(textFilter.toLowerCase(Locale.getDefault()))) continue
            newFiltered.add(f)
        }
        filtered = newFiltered
        updateGui()
    }

    private fun updateGui() {
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(filtered), true)
    }

    inner class RecyclerViewAdapter internal constructor(var foodList: List<Food>) : RecyclerView.Adapter<FoodsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): FoodsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.food_item, viewGroup, false)
            return FoodsViewHolder(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: FoodsViewHolder, position: Int) {
            val food = foodList[position]
            holder.binding.nsSign.visibility = if (food._id != null) View.VISIBLE else View.GONE
            holder.binding.name.text = food.name
            holder.binding.portion.text = food.portion.toString() + food.units
            holder.binding.carbs.text = food.carbs.toString() + resourceHelper.gs(R.string.shortgramm)
            holder.binding.fat.text = resourceHelper.gs(R.string.shortfat) + ": " + food.fat + resourceHelper.gs(R.string.shortgramm)
            if (food.fat == 0) holder.binding.fat.visibility = View.INVISIBLE
            holder.binding.protein.text = resourceHelper.gs(R.string.shortprotein) + ": " + food.protein + resourceHelper.gs(R.string.shortgramm)
            if (food.protein == 0) holder.binding.protein.visibility = View.INVISIBLE
            holder.binding.energy.text = resourceHelper.gs(R.string.shortenergy) + ": " + food.energy + resourceHelper.gs(R.string.shortkilojoul)
            if (food.energy == 0) holder.binding.energy.visibility = View.INVISIBLE
            holder.binding.remove.tag = food
        }

        override fun getItemCount(): Int = foodList.size

        inner class FoodsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = FoodItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val food = v.tag as Food
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.confirmation), resourceHelper.gs(R.string.removerecord) + "\n" + food.name, DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                            uel.log("FOOD REMOVED", food.name)
                            if (food._id != null && food._id != "") {
                                nsUpload.removeFoodFromNS(food._id)
                            }
                            foodPlugin.service?.delete(food)
                        }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}
