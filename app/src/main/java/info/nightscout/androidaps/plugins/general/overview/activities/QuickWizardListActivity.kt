package info.nightscout.androidaps.plugins.general.overview.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.data.QuickWizard
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.overview_quickwizardlist_activity.*

class QuickWizardListActivity : NoSplashAppCompatActivity() {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private inner class RecyclerViewAdapter internal constructor(internal var fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickWizardEntryViewHolder {
            return QuickWizardEntryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.overview_quickwizardlist_item, parent, false), fragmentManager)
        }

        override fun onBindViewHolder(holder: QuickWizardEntryViewHolder, position: Int) {
            holder.from.text = DateUtil.timeString(QuickWizard[position].validFromDate())
            holder.to.text = DateUtil.timeString(QuickWizard[position].validToDate())
            holder.buttonText.text = QuickWizard[position].buttonText()
            holder.carbs.text = DecimalFormatter.to0Decimal(QuickWizard[position].carbs().toDouble()) + " g"
        }

        override fun getItemCount(): Int = QuickWizard.size()

        private inner class QuickWizardEntryViewHolder internal constructor(itemView: View, internal var fragmentManager: FragmentManager) : RecyclerView.ViewHolder(itemView) {
            val buttonText: TextView = itemView.findViewById(R.id.overview_quickwizard_item_buttonText)
            val carbs: TextView = itemView.findViewById(R.id.overview_quickwizard_item_carbs)
            val from: TextView = itemView.findViewById(R.id.overview_quickwizard_item_from)
            val to: TextView = itemView.findViewById(R.id.overview_quickwizard_item_to)
            private val editButton: Button = itemView.findViewById(R.id.overview_quickwizard_item_edit_button)
            private val removeButton: Button = itemView.findViewById(R.id.overview_quickwizard_item_remove_button)

            init {
                editButton.setOnClickListener {
                    val manager = fragmentManager
                    val editQuickWizardDialog = EditQuickWizardDialog()
                    editQuickWizardDialog.entry = QuickWizard[adapterPosition]
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                }
                removeButton.setOnClickListener {
                    QuickWizard.remove(adapterPosition)
                    RxBus.send(EventQuickWizardChange())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.overview_quickwizardlist_activity)

        overview_quickwizardactivity_recyclerview?.setHasFixedSize(true)
        overview_quickwizardactivity_recyclerview?.layoutManager = LinearLayoutManager(this)
        overview_quickwizardactivity_recyclerview?.adapter = RecyclerViewAdapter(supportFragmentManager)

        overview_quickwizardactivity_add_button.setOnClickListener {
            val manager = supportFragmentManager
            val editQuickWizardDialog = EditQuickWizardDialog()
            editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
        }
    }

    override fun onResume() {
        super.onResume()
        disposable += RxBus
                .toObservable(EventQuickWizardChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val adapter = RecyclerViewAdapter(supportFragmentManager)
                    overview_quickwizardactivity_recyclerview?.swapAdapter(adapter, false)
                }, {
                    FabricPrivacy.logException(it)
                })
    }

    override fun onPause() {
        disposable.clear()
        super.onPause()
    }
}