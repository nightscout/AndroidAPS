package info.nightscout.androidaps.plugins.general.food

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
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventFoodDatabaseChanged
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.food.FoodFragment.RecyclerViewAdapter.FoodsViewHolder
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.food_fragment.*
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class FoodFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var foodPlugin: FoodPlugin
    @Inject lateinit var nsUpload: NSUpload

    private val disposable = CompositeDisposable()
    private lateinit var unfiltered: List<Food>
    private lateinit var filtered: MutableList<Food>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.food_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        food_recyclerview.setHasFixedSize(true)
        food_recyclerview.layoutManager = LinearLayoutManager(view.context)
        food_recyclerview.adapter = RecyclerViewAdapter(foodPlugin.service?.foodData ?: ArrayList())

        food_clearfilter.setOnClickListener {
            food_filter.setText("")
            food_category.setSelection(0)
            food_subcategory.setSelection(0)
            filterData()
        }
        food_category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fillSubcategories()
                filterData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                fillSubcategories()
                filterData()
            }
        }
        food_subcategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                filterData()
            }
        }
        food_filter.addTextChangedListener(object : TextWatcher {
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

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventFoodDatabaseChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }) { fabricPrivacy.logException(it) }
        )
        updateGui()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
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
            food_category.adapter = adapterCategories
        }
    }

    private fun fillSubcategories() {
        val categoryFilter = food_category.selectedItem.toString()
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
            food_subcategory.adapter = adapterSubcategories
        }
    }

    private fun filterData() {
        val textFilter = food_filter.text.toString()
        val categoryFilter = food_category.selectedItem.toString()
        val subcategoryFilter = food_subcategory.selectedItem.toString()
        val newfiltered = ArrayList<Food>()
        for (f in unfiltered) {
            if (f.name == null || f.category == null || f.subcategory == null) continue
            if (subcategoryFilter != resourceHelper.gs(R.string.none) && f.subcategory != subcategoryFilter) continue
            if (categoryFilter != resourceHelper.gs(R.string.none) && f.category != categoryFilter) continue
            if (textFilter != "" && !f.name.toLowerCase(Locale.getDefault()).contains(textFilter.toLowerCase(Locale.getDefault()))) continue
            newfiltered.add(f)
        }
        filtered = newfiltered
        updateGui()
    }

    protected fun updateGui() {
        food_recyclerview?.swapAdapter(RecyclerViewAdapter(filtered), true)
    }

    inner class RecyclerViewAdapter internal constructor(var foodList: List<Food>) : RecyclerView.Adapter<FoodsViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): FoodsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.food_item, viewGroup, false)
            return FoodsViewHolder(v)
        }

        override fun onBindViewHolder(holder: FoodsViewHolder, position: Int) {
            val food = foodList[position]
            holder.ns.visibility = if (food._id != null) View.VISIBLE else View.GONE
            holder.name.text = food.name
            holder.portion.text = food.portion.toString() + food.units
            holder.carbs.text = food.carbs.toString() + resourceHelper.gs(R.string.shortgramm)
            holder.fat.text = resourceHelper.gs(R.string.shortfat) + ": " + food.fat + resourceHelper.gs(R.string.shortgramm)
            if (food.fat == 0) holder.fat.visibility = View.INVISIBLE
            holder.protein.text = resourceHelper.gs(R.string.shortprotein) + ": " + food.protein + resourceHelper.gs(R.string.shortgramm)
            if (food.protein == 0) holder.protein.visibility = View.INVISIBLE
            holder.energy.text = resourceHelper.gs(R.string.shortenergy) + ": " + food.energy + resourceHelper.gs(R.string.shortkilojoul)
            if (food.energy == 0) holder.energy.visibility = View.INVISIBLE
            holder.remove.tag = food
        }

        override fun getItemCount(): Int = foodList.size

        inner class FoodsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var name: TextView = itemView.findViewById(R.id.food_name)
            var portion: TextView = itemView.findViewById(R.id.food_portion)
            var carbs: TextView = itemView.findViewById(R.id.food_carbs)
            var fat: TextView = itemView.findViewById(R.id.food_fat)
            var protein: TextView = itemView.findViewById(R.id.food_protein)
            var energy: TextView = itemView.findViewById(R.id.food_energy)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)
            var remove: TextView = itemView.findViewById(R.id.food_remove)

            init {
                remove.setOnClickListener { v: View ->
                    val food = v.tag as Food
                    activity?.let { activity ->
                        showConfirmation(activity, resourceHelper.gs(R.string.confirmation), resourceHelper.gs(R.string.removerecord) + "\n" + food.name, DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                            if (food._id != null && food._id != "") {
                                nsUpload.removeFoodFromNS(food._id)
                            }
                            foodPlugin.service?.delete(food)
                        }, null)
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}
