package info.nightscout.androidaps.activities.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import info.nightscout.androidaps.interfaces.ImportExportPrefs
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.userEntry.UserEntryPresentationHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsUserEntryFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var translator: Translator
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var userEntryPresentationHelper: UserEntryPresentationHelper

    private val disposable = CompositeDisposable()

    private val millsToThePastFiltered = T.days(30).msecs()
    private val millsToThePastUnFiltered = T.days(3).msecs()

    private var _binding: TreatmentsUserEntryFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsUserEntryFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.ueExportToXml.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.ue_export_to_csv) + "?") {
                    uel.log(Action.EXPORT_CSV, Sources.Treatments)
                    importExportPrefs.exportUserEntriesCsv(activity, repository.getAllUserEntries())
                }
            }
        }
        binding.showLoop.setOnCheckedChangeListener { _, _ ->
            rxBus.send(EventTreatmentUpdateGui())
        }
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        if (binding.showLoop.isChecked)
            disposable.add( repository
                .getUserEntryDataFromTime(now - millsToThePastUnFiltered)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
            )
        else
            disposable.add( repository
                .getUserEntryFilteredDataFromTime(now - millsToThePastFiltered)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
            )
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()

        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTreatmentUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException))
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
            holder.binding.date.text = dateUtil.dateAndTimeAndSecondsString(current.timestamp)
            holder.binding.action.text = userEntryPresentationHelper.actionToColoredString(current.action)
            holder.binding.s.text = current.note
            holder.binding.s.visibility = if (current.note != "") View.VISIBLE else View.GONE
            holder.binding.iconSource.setImageResource(userEntryPresentationHelper.iconId(current.source))
            holder.binding.iconSource.visibility = View.VISIBLE
            holder.binding.values.text = userEntryPresentationHelper.listToPresentationString(current.values)
            holder.binding.values.visibility = if (holder.binding.values.text != "") View.VISIBLE else View.GONE
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }

        override fun getItemCount(): Int = entries.size
    }

}