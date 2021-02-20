package info.nightscout.androidaps.plugins.treatments.fragments

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
import info.nightscout.androidaps.databinding.TreatmentsUserEntryFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsUserEntryItemBinding
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class TreatmentsUserEntryFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dateUtil: DateUtil

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

        disposable += repository
            .getAllUserEntries()
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
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
            holder.binding.action.text = current.action
            if (current.s != "") holder.binding.s.text = current.s else holder.binding.s.visibility = View.GONE
            if (current.d1 != 0.0) holder.binding.d1.text = current.d1.toString() else holder.binding.d1.visibility = View.GONE
            if (current.d2 != 0.0) holder.binding.d2.text = current.d2.toString() else holder.binding.d2.visibility = View.GONE
            if (current.i1 != 0) holder.binding.i1.text = current.i1.toString() else holder.binding.i1.visibility = View.GONE
            if (current.i2 != 0) holder.binding.i2.text = current.i2.toString() else holder.binding.i2.visibility = View.GONE
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }

        override fun getItemCount(): Int = entries.size

    }
}