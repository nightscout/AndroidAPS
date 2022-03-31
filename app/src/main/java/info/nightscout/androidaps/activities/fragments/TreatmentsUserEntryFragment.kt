package info.nightscout.androidaps.activities.fragments

import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.TreatmentsUserEntryFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsUserEntryItemBinding
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ImportExportPrefs
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.userEntry.UserEntryPresentationHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsUserEntryFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var translator: Translator
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var userEntryPresentationHelper: UserEntryPresentationHelper

    private val disposable = CompositeDisposable()
    private val millsToThePastFiltered = T.days(30).msecs()
    private val millsToThePastUnFiltered = T.days(3).msecs()
    private var menu: Menu? = null
    private var showLoop = false
    private var _binding: TreatmentsUserEntryFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsUserEntryFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    private fun exportUserEntries() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.ue_export_to_csv) + "?") {
                uel.log(Action.EXPORT_CSV, Sources.Treatments)
                importExportPrefs.exportUserEntriesCsv(activity)
            }
        }
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        disposable +=
            if (showLoop)
                repository
                    .getUserEntryDataFromTime(now - millsToThePastUnFiltered)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
            else
                repository
                    .getUserEntryFilteredDataFromTime(now - millsToThePastFiltered)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTreatmentUpdateGui::class.java)
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
        disposable.clear()
        _binding = null
    }

    inner class UserEntryAdapter internal constructor(var entries: List<UserEntry>) : RecyclerView.Adapter<UserEntryAdapter.UserEntryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserEntryViewHolder {
            val view: View = LayoutInflater.from(parent.context).inflate(R.layout.treatments_user_entry_item, parent, false)
            return UserEntryViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserEntryViewHolder, position: Int) {
            val current = entries[position]
            val newDay = position == 0 || !dateUtil.isSameDayGroup(current.timestamp, entries[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(current.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeStringWithSeconds(current.timestamp)
            holder.binding.action.text = userEntryPresentationHelper.actionToColoredString(current.action)
            holder.binding.notes.text = current.note
            holder.binding.notes.visibility = (current.note != "").toVisibility()
            holder.binding.iconSource.setImageResource(userEntryPresentationHelper.iconId(current.source))
            holder.binding.values.text = userEntryPresentationHelper.listToPresentationString(current.values)
            holder.binding.values.visibility = (holder.binding.values.text != "").toVisibility()
            val nextTimestamp = if (entries.size != position + 1) entries[position + 1].timestamp else 0L
            holder.binding.delimiter.visibility = dateUtil.isSameDayGroup(current.timestamp, nextTimestamp).toVisibility()
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }

        override fun getItemCount() = entries.size
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_user_entry, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_loop)?.isVisible = showLoop
        menu?.findItem(R.id.nav_show_loop)?.isVisible = !showLoop
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        updateMenuVisibility()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_show_loop -> {
                showLoop = true
                updateMenuVisibility()
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.show_loop_records))
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_hide_loop -> {
                showLoop = false
                updateMenuVisibility()
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.show_hide_records))
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_export    -> {
                exportUserEntries()
                true
            }

            else               -> false
        }

}
