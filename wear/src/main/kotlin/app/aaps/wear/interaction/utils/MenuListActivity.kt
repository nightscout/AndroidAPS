package app.aaps.wear.interaction.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.CurvedTextView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableLinearLayoutManager.LayoutCallback
import androidx.wear.widget.WearableRecyclerView
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventUpdateSelectedWatchface
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.MenuListActivity.MenuAdapter.ItemViewHolder
import dagger.android.DaggerActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

/**
 * Created by adrian on 08/02/17.
 */
abstract class MenuListActivity : DaggerActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var elements: List<MenuItem> = listOf()
    private var disposable = CompositeDisposable()
    protected abstract fun provideElements(): List<MenuItem>
    protected abstract fun doAction(position: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.actions_list_activity)
        setTitleBasedOnScreenShape(title.toString())
        disposable += rxBus
            .toObservable(EventUpdateSelectedWatchface::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { _: EventUpdateSelectedWatchface ->
                updateMenu()
            }
        updateMenu()
    }

    override fun onDestroy() {
        findViewById<WearableRecyclerView>(R.id.action_list)?.adapter = null
        disposable.clear()
        super.onDestroy()
    }

    private fun updateMenu() {
        elements = provideElements()
        val customScrollingLayoutCallback = CustomScrollingLayoutCallback()
        val layoutManager = WearableLinearLayoutManager(this)
        val listView = findViewById<WearableRecyclerView>(R.id.action_list)
        val isScreenRound = this.resources.configuration.isScreenRound
        if (isScreenRound) {
            layoutManager.layoutCallback = customScrollingLayoutCallback
            listView.isEdgeItemsCenteringEnabled = true
        } else {
            // Bug in androidx.wear:wear:1.2.0
            // WearableRecyclerView setEdgeItemsCenteringEnabled requires fix for square screen
            listView.setPadding(0, 50, 0, 0)
        }
        listView.setHasFixedSize(true)
        listView.layoutManager = layoutManager
        listView.adapter = MenuAdapter(elements) { v: ItemViewHolder ->
            val tag = v.itemView.tag as String
            doAction(tag)
        }
    }

    private fun setTitleBasedOnScreenShape(title: String) {
        val titleViewCurved = findViewById<CurvedTextView>(R.id.title_curved)
        val titleView = findViewById<TextView>(R.id.title)
        if (this.resources.configuration.isScreenRound) {
            titleViewCurved.text = title
            titleViewCurved.visibility = View.VISIBLE
            titleView.visibility = View.GONE
        } else {
            titleView.text = title
            titleView.visibility = View.VISIBLE
            titleViewCurved.visibility = View.GONE
        }
    }

    class MenuAdapter(private val mDataset: List<MenuItem>, private val callback: (ItemViewHolder) -> Unit) : RecyclerView.Adapter<ItemViewHolder>() {
        class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val menuContainer: RelativeLayout = itemView.findViewById(R.id.menu_container)
            val actionItem: TextView = itemView.findViewById(R.id.menuItemText)
            val actionIcon: ImageView = itemView.findViewById(R.id.menuItemIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = mDataset[position]
            holder.actionItem.text = item.actionItem
            holder.actionIcon.setImageResource(item.actionIcon)
            holder.itemView.tag = item.actionItem
            holder.menuContainer.setOnClickListener { callback(holder) }
        }

        override fun getItemCount(): Int {
            return mDataset.size
        }
    }

    class MenuItem(var actionIcon: Int, var actionItem: String)
    class CustomScrollingLayoutCallback : LayoutCallback() {

        override fun onLayoutFinished(child: View, parent: RecyclerView) {
            // Figure out % progress from top to bottom
            val centerOffset = child.height.toFloat() / 2.0f / parent.height.toFloat()
            val yRelativeToCenterOffset = child.y / parent.height + centerOffset

            // Normalize for center
            var progressToCenter = abs(0.5f - yRelativeToCenterOffset)
            // Adjust to the maximum scale
            progressToCenter = min(progressToCenter, MAX_ICON_PROGRESS)
            child.scaleX = 1 - progressToCenter
            child.scaleY = 1 - progressToCenter
        }

        companion object {

            // How much should we scale the icon at most.
            private const val MAX_ICON_PROGRESS = 0.65f
        }
    }
}
