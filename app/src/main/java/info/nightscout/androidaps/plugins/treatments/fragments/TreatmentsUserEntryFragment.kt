package info.nightscout.androidaps.plugins.treatments.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.databinding.TreatmentsUserEntryFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsUserEntryItemBinding
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.ImportExportPrefsInterface
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.treatments.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.UserEntryPresentationHelper
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
    @Inject lateinit var importExportPrefs: ImportExportPrefsInterface
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var userEntryPresentationHelper: UserEntryPresentationHelper

    private val disposable = CompositeDisposable()

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
                    uel.log(Action.EXPORT_CSV, ValueWithUnit(Sources.Treatments))
                    importExportPrefs.exportUserEntriesCsv(activity, repository.getAllUserEntries())
                }
            }
        }
        binding.showLoop.setOnCheckedChangeListener { _, _ ->
            rxBus.send(EventTreatmentUpdateGui())
        }
    }

    fun swapAdapter() {
        if (binding.showLoop.isChecked)
            disposable.add( repository
                .getAllUserEntries()
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
            )
        else
            disposable.add( repository
                .getAllUserEntries()
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(filterUserEntries(list)), true) }
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

            if (current.s != "") {
                holder.binding.s.text = current.s
                holder.binding.s.visibility = View.VISIBLE
            } else
                holder.binding.s.visibility = View.GONE
            var valuesWithUnitString = ""
            var rStringParam = 0
            var source = Sources.Unknown
            val separator = "  "
            for(v in current.values) {
                if (rStringParam >0)
                    rStringParam--
                else
                    when (v.unit) {
                        Units.Timestamp    -> valuesWithUnitString += dateUtil.dateAndTimeAndSecondsString(v.lValue) + separator
                        Units.TherapyEvent -> valuesWithUnitString += translator.translate(v.sValue) + separator
                        Units.R_String     -> {
                            rStringParam = v.lValue.toInt()
                            when (rStringParam) {   //
                                0 -> valuesWithUnitString += resourceHelper.gs(v.iValue) + separator
                                1 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value()) + separator
                                2 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value(), current.values[current.values.indexOf(v)+2].value()) + separator
                                3 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value(), current.values[current.values.indexOf(v)+2].value(), current.values[current.values.indexOf(v)+3].value()) + separator
                                4 -> rStringParam = 0
                            }
                        }
                        Units.Mg_Dl     -> valuesWithUnitString += if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue) + translator.translate(Units.Mg_Dl) + separator else DecimalFormatter.to1Decimal(v.dValue/Constants.MMOLL_TO_MGDL) + translator.translate(Units.Mmol_L) + separator
                        Units.Mmol_L    -> valuesWithUnitString += if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue*Constants.MMOLL_TO_MGDL) + translator.translate(Units.Mg_Dl) + separator else DecimalFormatter.to1Decimal(v.dValue) + translator.translate(Units.Mmol_L) + separator
                        Units.U_H, Units.U
                                        -> valuesWithUnitString += DecimalFormatter.to2Decimal(v.dValue) + translator.translate(v.unit) + separator
                        Units.G, Units.M, Units.H, Units.Percent
                                        -> valuesWithUnitString += v.iValue.toString() + translator.translate(v.unit) + separator
                        Units.Source    -> source = Sources.fromText(v.sValue) // = separator + translator.translate(v.sValue)
                        else            -> valuesWithUnitString += if (v.iValue != 0 || v.sValue != "") { v.value().toString() + separator } else ""
                    }
            }

            holder.binding.iconSource.setImageResource(userEntryPresentationHelper.iconId(source))
            holder.binding.iconSource.visibility = View.VISIBLE
            holder.binding.values.text = valuesWithUnitString.trim()
            holder.binding.values.visibility = if (holder.binding.values.text != "") View.VISIBLE else View.GONE
            holder.binding.action.text = translator.translate(current.action)
            holder.binding.action.setTextColor(resourceHelper.gc(userEntryPresentationHelper.colorId(current.action.colorGroup)))
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }

        override fun getItemCount(): Int = entries.size
    }

    fun filterUserEntries(list: List<UserEntry>): List<UserEntry> {
        val filteredList = mutableListOf<UserEntry>()
        for (ue in list) {
            if (! ue.isLoop()) filteredList.add(ue)
        }
        return filteredList
    }
}