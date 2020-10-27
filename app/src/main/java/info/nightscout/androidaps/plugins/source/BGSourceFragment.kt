package info.nightscout.androidaps.plugins.source

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.source.BGSourceFragment.RecyclerViewAdapter.BgReadingsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.bgsource_fragment.*
import javax.inject.Inject

class BGSourceFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var databaseHelper: DatabaseHelperInterface

    private val disposable = CompositeDisposable()
    private val MILLS_TO_THE_PAST = T.hours(12).msecs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bgsource_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bgsource_recyclerview.setHasFixedSize(true)
        bgsource_recyclerview.layoutManager = LinearLayoutManager(view.context)
        val now = System.currentTimeMillis()
        bgsource_recyclerview.adapter = RecyclerViewAdapter(MainApp.getDbHelper().getAllBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false))
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }) { fabricPrivacy.logException(it) }
        )
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        disposable.clear()
        super.onPause()
    }

    private fun updateGUI() {
        val now = System.currentTimeMillis()
        bgsource_recyclerview?.swapAdapter(RecyclerViewAdapter(MainApp.getDbHelper().getAllBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false)), true)
    }

    inner class RecyclerViewAdapter internal constructor(private var bgReadings: List<BgReading>) : RecyclerView.Adapter<BgReadingsViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): BgReadingsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.bgsource_item, viewGroup, false)
            return BgReadingsViewHolder(v)
        }

        override fun onBindViewHolder(holder: BgReadingsViewHolder, position: Int) {
            val bgReading = bgReadings[position]
            holder.ns.visibility = if (NSUpload.isIdValid(bgReading._id)) View.VISIBLE else View.GONE
            holder.invalid.visibility = if (!bgReading.isValid) View.VISIBLE else View.GONE
            holder.date.text = dateUtil.dateAndTimeString(bgReading.date)
            holder.value.text = bgReading.valueToUnitsToString(profileFunction.getUnits())
            holder.direction.setImageResource(bgReading.directionToIcon(databaseHelper))
            holder.remove.tag = bgReading
        }

        override fun getItemCount(): Int {
            return bgReadings.size
        }

        inner class BgReadingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var date: TextView = itemView.findViewById(R.id.bgsource_date)
            var value: TextView = itemView.findViewById(R.id.bgsource_value)
            var direction: ImageView = itemView.findViewById(R.id.bgsource_direction)
            var invalid: TextView = itemView.findViewById(R.id.invalid_sign)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)
            var remove: TextView = itemView.findViewById(R.id.bgsource_remove)

            init {
                remove.setOnClickListener { v: View ->
                    val bgReading = v.tag as BgReading
                    activity?.let { activity ->
                        val text = dateUtil.dateAndTimeString(bgReading.date) + "\n" + bgReading.valueToUnitsToString(profileFunction.getUnits())
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            bgReading.isValid = false
                            MainApp.getDbHelper().update(bgReading)
                            updateGUI()
                        })
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}