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
import info.nightscout.androidaps.databinding.OverviewQuickwizardlistActivityBinding
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.wizard.QuickWizard
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class QuickWizardListActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil

    private var disposable: CompositeDisposable = CompositeDisposable()

    private lateinit var binding: OverviewQuickwizardlistActivityBinding

    private inner class RecyclerViewAdapter(var fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickWizardEntryViewHolder {
            return QuickWizardEntryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.overview_quickwizardlist_item, parent, false), fragmentManager)
        }

        override fun onBindViewHolder(holder: QuickWizardEntryViewHolder, position: Int) {
            holder.from.text = dateUtil.timeString(quickWizard[position].validFromDate())
            holder.to.text = dateUtil.timeString(quickWizard[position].validToDate())
            holder.buttonText.text = quickWizard[position].buttonText()
            holder.carbs.text = resourceHelper.gs(R.string.format_carbs, quickWizard[position].carbs())
        }

        override fun getItemCount(): Int = quickWizard.size()

        private inner class QuickWizardEntryViewHolder(itemView: View, var fragmentManager: FragmentManager) : RecyclerView.ViewHolder(itemView) {

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
                    val bundle = Bundle()
                    bundle.putInt("position", adapterPosition)
                    editQuickWizardDialog.arguments = bundle
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                }
                removeButton.setOnClickListener {
                    quickWizard.remove(adapterPosition)
                    rxBus.send(EventQuickWizardChange())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverviewQuickwizardlistActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(supportFragmentManager)

        binding.addButton.setOnClickListener {
            val manager = supportFragmentManager
            val editQuickWizardDialog = EditQuickWizardDialog()
            editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
        }
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventQuickWizardChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                val adapter = RecyclerViewAdapter(supportFragmentManager)
                binding.recyclerview.swapAdapter(adapter, false)
            }, fabricPrivacy::logException)
    }

    override fun onPause() {
        disposable.clear()
        super.onPause()
    }
}