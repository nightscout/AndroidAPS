package info.nightscout.androidaps.plugins.constraints.objectives

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import info.nightscout.androidaps.dialogs.NtpProgressDialog
import info.nightscout.androidaps.events.EventNtpStatus
import info.nightscout.androidaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.SntpClient
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.objectives_fragment.*
import javax.inject.Inject

class ObjectivesFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sntpClient: SntpClient

    private val objectivesAdapter = ObjectivesAdapter()
    private val handler = Handler(Looper.getMainLooper())

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val objectiveUpdater = object : Runnable {
        override fun run() {
            handler.postDelayed(this, (60 * 1000).toLong())
            updateGUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.objectives_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        objectives_recyclerview.layoutManager = LinearLayoutManager(view.context)
        objectives_recyclerview.adapter = objectivesAdapter
        objectives_fake.setOnClickListener { updateGUI() }
        objectives_reset.setOnClickListener {
            objectivesPlugin.reset()
            objectives_recyclerview.adapter?.notifyDataSetChanged()
            scrollToCurrentObjective()
        }
        scrollToCurrentObjective()
        startUpdateTimer()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventObjectivesUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                objectives_recyclerview.adapter?.notifyDataSetChanged()
            }, { fabricPrivacy.logException(it) }
            )
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(objectiveUpdater)
    }

    private fun startUpdateTimer() {
        handler.removeCallbacks(objectiveUpdater)
        for (objective in objectivesPlugin.objectives) {
            if (objective.isStarted && !objective.isAccomplished) {
                val timeTillNextMinute = (System.currentTimeMillis() - objective.startedOn) % (60 * 1000)
                handler.postDelayed(objectiveUpdater, timeTillNextMinute)
                break
            }
        }
    }

    private fun scrollToCurrentObjective() {
        activity?.runOnUiThread {
            for (i in 0 until objectivesPlugin.objectives.size) {
                val objective = objectivesPlugin.objectives[i]
                if (!objective.isStarted || !objective.isAccomplished) {
                    context?.let {
                        val smoothScroller = object : LinearSmoothScroller(it) {
                            override fun getVerticalSnapPreference(): Int = SNAP_TO_START
                            override fun calculateTimeForScrolling(dx: Int): Int = super.calculateTimeForScrolling(dx) * 4
                        }
                        smoothScroller.targetPosition = i
                        objectives_recyclerview.layoutManager?.startSmoothScroll(smoothScroller)
                    }
                    break
                }
            }
        }
    }

    private inner class ObjectivesAdapter : RecyclerView.Adapter<ObjectivesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.objectives_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val objective = objectivesPlugin.objectives[position]
            holder.title.text = resourceHelper.gs(R.string.nth_objective, position + 1)
            if (objective.objective != 0) {
                holder.objective.visibility = View.VISIBLE
                holder.objective.text = resourceHelper.gs(objective.objective)
            } else
                holder.objective.visibility = View.GONE
            if (objective.gate != 0) {
                holder.gate.visibility = View.VISIBLE
                holder.gate.text = resourceHelper.gs(objective.gate)
            } else
                holder.gate.visibility = View.GONE
            if (!objective.isStarted) {
                holder.gate.setTextColor(-0x1)
                holder.verify.visibility = View.GONE
                holder.progress.visibility = View.GONE
                holder.accomplished.visibility = View.GONE
                holder.unFinish.visibility = View.GONE
                holder.unStart.visibility = View.GONE
                if (position == 0 || objectivesPlugin.allPriorAccomplished(position))
                    holder.start.visibility = View.VISIBLE
                else
                    holder.start.visibility = View.GONE
            } else if (objective.isAccomplished) {
                holder.gate.setTextColor(-0xb350b0)
                holder.verify.visibility = View.GONE
                holder.progress.visibility = View.GONE
                holder.start.visibility = View.GONE
                holder.accomplished.visibility = View.VISIBLE
                holder.unFinish.visibility = View.VISIBLE
                holder.unStart.visibility = View.GONE
            } else if (objective.isStarted) {
                holder.gate.setTextColor(-0x1)
                holder.verify.visibility = View.VISIBLE
                holder.verify.isEnabled = objective.isCompleted || objectives_fake.isChecked
                holder.start.visibility = View.GONE
                holder.accomplished.visibility = View.GONE
                holder.unFinish.visibility = View.GONE
                holder.unStart.visibility = View.VISIBLE
                holder.progress.visibility = View.VISIBLE
                holder.progress.removeAllViews()
                for (task in objective.tasks) {
                    if (task.shouldBeIgnored()) continue
                    // name
                    val name = TextView(holder.progress.context)
                    @Suppress("SetTextlI8n")
                    name.text = resourceHelper.gs(task.task) + ":"
                    name.setTextColor(-0x1)
                    holder.progress.addView(name, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    // hint
                    task.hints.forEach { h ->
                        if (!task.isCompleted)
                            holder.progress.addView(h.generate(context))
                    }
                    // state
                    val state = TextView(holder.progress.context)
                    state.setTextColor(-0x1)
                    val basicHTML = "<font color=\"%1\$s\"><b>%2\$s</b></font>"
                    val formattedHTML = String.format(basicHTML, if (task.isCompleted) "#4CAF50" else "#FF9800", task.progress)
                    state.text = HtmlHelper.fromHtml(formattedHTML)
                    state.gravity = Gravity.END
                    holder.progress.addView(state, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    if (task is ExamTask) {
                        state.setOnClickListener {
                            val dialog = ObjectivesExamDialog()
                            val bundle = Bundle()
                            val taskPosition = objective.tasks.indexOf(task)
                            bundle.putInt("currentTask", taskPosition)
                            dialog.arguments = bundle
                            ObjectivesExamDialog.objective = objective
                            dialog.show(childFragmentManager, "ObjectivesFragment")
                        }
                    }
                    // horizontal line
                    val separator = View(holder.progress.context)
                    separator.setBackgroundColor(Color.DKGRAY)
                    holder.progress.addView(separator, LinearLayout.LayoutParams.MATCH_PARENT, 2)
                }
            }
            holder.accomplished.text = resourceHelper.gs(R.string.accomplished, dateUtil.dateAndTimeString(objective.accomplishedOn))
            holder.accomplished.setTextColor(-0x3e3e3f)
            holder.verify.setOnClickListener {
                receiverStatusStore.updateNetworkStatus()
                if (objectives_fake.isChecked) {
                    objective.accomplishedOn = DateUtil.now()
                    scrollToCurrentObjective()
                    startUpdateTimer()
                    rxBus.send(EventObjectivesUpdateGui())
                    rxBus.send(EventSWUpdate(false))
                } else {
                    // move out of UI thread
                    Thread {
                        NtpProgressDialog().show((context as AppCompatActivity).supportFragmentManager, "NtpCheck")
                        rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.timedetection), 0))
                        sntpClient.ntpTime(object : SntpClient.Callback() {
                            override fun run() {
                                aapsLogger.debug("NTP time: $time System time: ${DateUtil.now()}")
                                SystemClock.sleep(300)
                                if (!networkConnected) {
                                    rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.notconnected), 99))
                                } else if (success) {
                                    if (objective.isCompleted(time)) {
                                        objective.accomplishedOn = time
                                        rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.success), 100))
                                        SystemClock.sleep(1000)
                                        rxBus.send(EventObjectivesUpdateGui())
                                        rxBus.send(EventSWUpdate(false))
                                        SystemClock.sleep(100)
                                        scrollToCurrentObjective()
                                    } else {
                                        rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.requirementnotmet), 99))
                                    }
                                } else {
                                    rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.failedretrievetime), 99))
                                }
                            }
                        }, receiverStatusStore.isConnected)
                    }.start()
                }
            }
            holder.start.setOnClickListener {
                receiverStatusStore.updateNetworkStatus()
                if (objectives_fake.isChecked) {
                    objective.startedOn = DateUtil.now()
                    scrollToCurrentObjective()
                    startUpdateTimer()
                    rxBus.send(EventObjectivesUpdateGui())
                    rxBus.send(EventSWUpdate(false))
                } else
                // move out of UI thread
                    Thread {
                        NtpProgressDialog().show((context as AppCompatActivity).supportFragmentManager, "NtpCheck")
                        rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.timedetection), 0))
                        sntpClient.ntpTime(object : SntpClient.Callback() {
                            override fun run() {
                                aapsLogger.debug("NTP time: $time System time: ${DateUtil.now()}")
                                SystemClock.sleep(300)
                                if (!networkConnected) {
                                    rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.notconnected), 99))
                                } else if (success) {
                                    objective.startedOn = time
                                    rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.success), 100))
                                    SystemClock.sleep(1000)
                                    rxBus.send(EventObjectivesUpdateGui())
                                    rxBus.send(EventSWUpdate(false))
                                    SystemClock.sleep(100)
                                    scrollToCurrentObjective()
                                } else {
                                    rxBus.send(EventNtpStatus(resourceHelper.gs(R.string.failedretrievetime), 99))
                                }
                            }
                        }, receiverStatusStore.isConnected)
                    }.start()
            }
            holder.unStart.setOnClickListener {
                activity?.let { activity ->
                    OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.objectives), resourceHelper.gs(R.string.doyouwantresetstart), Runnable {
                        objective.startedOn = 0
                        scrollToCurrentObjective()
                        rxBus.send(EventObjectivesUpdateGui())
                        rxBus.send(EventSWUpdate(false))
                    })
                }
            }
            holder.unFinish.setOnClickListener {
                objective.accomplishedOn = 0
                scrollToCurrentObjective()
                rxBus.send(EventObjectivesUpdateGui())
                rxBus.send(EventSWUpdate(false))
            }
            if (objective.hasSpecialInput && !objective.isAccomplished && objective.isStarted && objective.specialActionEnabled()) {
                // generate random request code if none exists
                val request = sp.getString(R.string.key_objectives_request_code, String.format("%1$05d", (Math.random() * 99999).toInt()))
                sp.putString(R.string.key_objectives_request_code, request)
                holder.requestCode.text = resourceHelper.gs(R.string.requestcode, request)
                holder.requestCode.visibility = View.VISIBLE
                holder.enterButton.visibility = View.VISIBLE
                holder.input.visibility = View.VISIBLE
                holder.inputHint.visibility = View.VISIBLE
                holder.enterButton.setOnClickListener {
                    val input = holder.input.text.toString()
                    objective.specialAction(activity, input)
                    rxBus.send(EventObjectivesUpdateGui())
                }
            } else {
                holder.enterButton.visibility = View.GONE
                holder.input.visibility = View.GONE
                holder.inputHint.visibility = View.GONE
                holder.requestCode.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int {
            return objectivesPlugin.objectives.size
        }

        inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.objective_title)
            val objective: TextView = itemView.findViewById(R.id.objective_objective)
            val gate: TextView = itemView.findViewById(R.id.objective_gate)
            val accomplished: TextView = itemView.findViewById(R.id.objective_accomplished)
            val progress: LinearLayout = itemView.findViewById(R.id.objective_progress)
            val verify: Button = itemView.findViewById(R.id.objective_verify)
            val start: Button = itemView.findViewById(R.id.objective_start)
            val unFinish: Button = itemView.findViewById(R.id.objective_unfinish)
            val unStart: Button = itemView.findViewById(R.id.objective_unstart)
            val inputHint: TextView = itemView.findViewById(R.id.objective_inputhint)
            val input: EditText = itemView.findViewById(R.id.objective_input)
            val enterButton: Button = itemView.findViewById(R.id.objective_enterbutton)
            val requestCode: TextView = itemView.findViewById(R.id.objective_requestcode)
        }
    }

    fun updateGUI() {
        activity?.runOnUiThread { objectivesAdapter.notifyDataSetChanged() }
    }
}
