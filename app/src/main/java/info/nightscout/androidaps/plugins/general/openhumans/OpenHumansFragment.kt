package info.nightscout.androidaps.plugins.general.openhumans

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.BackpressureStrategy
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OpenHumansFragment : DaggerFragment() {

    private var viewsCreated = false
    private var login: Button? = null
    private var logout: Button? = null
    private var memberId: TextView? = null
    private var queueSize: TextView? = null
    private var workerState: TextView? = null
    private var queueSizeValue = 0L
    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var openHumansUploader: OpenHumansUploader
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        compositeDisposable += Single.fromCallable { MainApp.getDbHelper().ohQueueSize }
            .subscribeOn(aapsSchedulers.io)
            .repeatWhen {
                rxBus.toObservable(UpdateViewEvent::class.java)
                    .cast(Any::class.java)
                    .mergeWith(rxBus.toObservable(UpdateQueueEvent::class.java)
                        .throttleLatest(5, TimeUnit.SECONDS))
                    .toFlowable(BackpressureStrategy.LATEST)
            }
            .observeOn(aapsSchedulers.main)
            .subscribe({
                queueSizeValue = it
                updateGUI()
            }, {})
        context?.applicationContext?.let { appContext ->
            WorkManager.getInstance(appContext).getWorkInfosForUniqueWorkLiveData(OpenHumansUploader.WORK_NAME).observe(this, Observer<List<WorkInfo>> {
                val workInfo = it.lastOrNull()
                if (workInfo == null) {
                    workerState?.visibility = View.GONE
                } else {
                    workerState?.visibility = View.VISIBLE
                    workerState?.text = getString(R.string.worker_state, workInfo.state.toString())
                }
            })
        }
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
        logout!!.setOnClickListener {
            activity?.let { activity -> OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.oh_logout_confirmation), Runnable { openHumansUploader.logout() }) }
        }
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
            val projectMemberId = openHumansUploader.projectMemberId
            memberId!!.text = getString(R.string.project_member_id, projectMemberId
                ?: getString(R.string.not_logged_in))
            login!!.visibility = if (projectMemberId == null) View.VISIBLE else View.GONE
            logout!!.visibility = if (projectMemberId != null) View.VISIBLE else View.GONE
        }
    }

    object UpdateViewEvent : Event()

    object UpdateQueueEvent : Event()
}