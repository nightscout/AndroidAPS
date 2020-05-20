package info.nightscout.androidaps.plugins.general.openhumans

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class OpenHumansFragment : Fragment() {

    private var viewsCreated = false
    private var login: Button? = null
    private var logout: Button? = null
    private var memberId: TextView? = null
    private var queueSize: TextView? = null
    private var workerState: TextView? = null
    private var queueSizeValue = 0L
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        compositeDisposable += RxBus.toObservable(UpdateQueueEvent::class.java)
            .throttleLatest(5, TimeUnit.SECONDS)
            .observeOn(Schedulers.io())
            .map { MainApp.getDbHelper().ohQueueSize }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                queueSizeValue = it
                updateGUI()
            }
        compositeDisposable += RxBus.toObservable(UpdateViewEvent::class.java)
            .observeOn(Schedulers.io())
            .map { MainApp.getDbHelper().ohQueueSize }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                queueSizeValue = it
                updateGUI()
            }
        WorkManager.getInstance(MainApp.instance()).getWorkInfosForUniqueWorkLiveData(OpenHumansUploader.WORK_NAME).observe(this, Observer<List<WorkInfo>> {
            val workInfo = it.lastOrNull()
            if (workInfo == null) {
                workerState?.visibility = View.GONE
            } else {
                workerState?.visibility = View.VISIBLE
                workerState?.text = getString(R.string.worker_state, workInfo.state.toString())
            }
        })
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_open_humans, container, false)
        login = view.findViewById(R.id.login)
        logout = view.findViewById(R.id.logout)
        memberId = view.findViewById(R.id.member_id)
        queueSize = view.findViewById(R.id.queue_size)
        workerState = view.findViewById(R.id.worker_state)
        login!!.setOnClickListener { startActivity(Intent(context, OpenHumansLoginActivity::class.java)) }
        logout!!.setOnClickListener { OpenHumansUploader.logout() }
        viewsCreated = true
        updateGUI()
        return view
    }

    override fun onDestroyView() {
        viewsCreated = false
        login = null
        logout = null
        memberId = null
        queueSize = null
        super.onDestroyView()
    }

    fun updateGUI() {
        if (viewsCreated) {
            queueSize!!.text = getString(R.string.queue_size, queueSizeValue)
            val projectMemberId = OpenHumansUploader.projectMemberId
            memberId!!.text = getString(R.string.project_member_id, projectMemberId ?: getString(R.string.not_logged_in))
            login!!.visibility = if (projectMemberId == null) View.VISIBLE else View.GONE
            logout!!.visibility = if (projectMemberId != null) View.VISIBLE else View.GONE
        }
    }

    object UpdateViewEvent : Event()

    object UpdateQueueEvent : Event()
}