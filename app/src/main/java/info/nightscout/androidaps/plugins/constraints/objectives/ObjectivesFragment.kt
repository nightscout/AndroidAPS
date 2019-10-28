package info.nightscout.androidaps.plugins.constraints.objectives

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import info.nightscout.androidaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import info.nightscout.androidaps.receivers.NetworkChangeReceiver
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.objectives_fragment.*
import org.slf4j.LoggerFactory

class ObjectivesFragment : Fragment() {
    private val log = LoggerFactory.getLogger(L.CONSTRAINTS)
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
            ObjectivesPlugin.reset()
            objectives_recyclerview.adapter?.notifyDataSetChanged()
            scrollToCurrentObjective()
        }
        scrollToCurrentObjective()
        startUpdateTimer()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventObjectivesUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    objectives_recyclerview.adapter?.notifyDataSetChanged()
                }, {
                    FabricPrivacy.logException(it)
                })
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
        for (objective in ObjectivesPlugin.objectives) {
            if (objective.isStarted && !objective.isAccomplished) {
                val timeTillNextMinute = (System.currentTimeMillis() - objective.startedOn) % (60 * 1000)
                handler.postDelayed(objectiveUpdater, timeTillNextMinute)
                break
            }
        }
    }

    private fun scrollToCurrentObjective() {
        for (i in 0 until ObjectivesPlugin.objectives.size) {
            val objective = ObjectivesPlugin.objectives[i]
            if (!objective.isStarted || !objective.isAccomplished) {
                val smoothScroller = object : LinearSmoothScroller(context!!) {
                    override fun getVerticalSnapPreference(): Int {
                        return SNAP_TO_START
                    }

                    override fun calculateTimeForScrolling(dx: Int): Int {
                        return super.calculateTimeForScrolling(dx) * 4
                    }
                }
                smoothScroller.targetPosition = i
                objectives_recyclerview.layoutManager?.startSmoothScroll(smoothScroller)
                break
            }
        }
    }

    private inner class ObjectivesAdapter : RecyclerView.Adapter<ObjectivesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.objectives_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val objective = ObjectivesPlugin.objectives[position]
            holder.title.text = MainApp.gs(R.string.nth_objective, position + 1)
            holder.revert.visibility = View.GONE
            if (objective.objective != 0) {
                holder.objective.visibility = View.VISIBLE
                holder.objective.text = MainApp.gs(objective.objective)
            } else
                holder.objective.visibility = View.GONE
            if (objective.gate != 0) {
                holder.gate.visibility = View.VISIBLE
                holder.gate.text = MainApp.gs(objective.gate)
            } else
                holder.gate.visibility = View.GONE
            if (!objective.isStarted) {
                holder.gate.setTextColor(-0x1)
                holder.verify.visibility = View.GONE
                holder.progress.visibility = View.GONE
                holder.accomplished.visibility = View.GONE
                if (position == 0 || ObjectivesPlugin.objectives[position - 1].isAccomplished)
                    holder.start.visibility = View.VISIBLE
                else
                    holder.start.visibility = View.GONE
            } else if (objective.isAccomplished) {
                holder.gate.setTextColor(-0xb350b0)
                holder.verify.visibility = View.GONE
                holder.progress.visibility = View.GONE
                holder.start.visibility = View.GONE
                holder.accomplished.visibility = View.VISIBLE
            } else if (objective.isStarted) {
                holder.gate.setTextColor(-0x1)
                holder.verify.visibility = View.VISIBLE
                holder.verify.isEnabled = objective.isCompleted || objectives_fake.isChecked
                holder.start.visibility = View.GONE
                holder.accomplished.visibility = View.GONE
                if (objective.isRevertable) {
                    holder.revert.visibility = View.VISIBLE
                }
                holder.progress.visibility = View.VISIBLE
                holder.progress.removeAllViews()
                for (task in objective.tasks) {
                    if (task.shouldBeIgnored()) continue
                    // name
                    val name = TextView(holder.progress.context)
                    name.text = MainApp.gs(task.task) + ":"
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
                            fragmentManager?.let { dialog.show(it, "ObjectivesFragment") }
                        }
                    }
                    // horizontal line
                    val separator = View(holder.progress.context)
                    separator.setBackgroundColor(Color.DKGRAY)
                    holder.progress.addView(separator, LinearLayout.LayoutParams.MATCH_PARENT, 2)
                }
            }
            holder.accomplished.text = MainApp.gs(R.string.accomplished, DateUtil.dateAndTimeString(objective.accomplishedOn))
            holder.accomplished.setTextColor(-0x3e3e3f)
            holder.verify.setOnClickListener {
                holder.verify.visibility = View.INVISIBLE
                NetworkChangeReceiver.fetch()
                if (objectives_fake.isChecked) {
                    objective.accomplishedOn = DateUtil.now()
                    scrollToCurrentObjective()
                    startUpdateTimer()
                    RxBus.send(EventObjectivesUpdateGui())
                } else
                    SntpClient.ntpTime(object : SntpClient.Callback() {
                        override fun run() {
                            activity?.runOnUiThread {
                                holder.verify.visibility = View.VISIBLE
                                log.debug("NTP time: $time System time: ${DateUtil.now()}")
                                if (!networkConnected) {
                                    ToastUtils.showToastInUiThread(context, R.string.notconnected)
                                } else if (success) {
                                    if (objective.isCompleted(time)) {
                                        objective.accomplishedOn = time
                                        scrollToCurrentObjective()
                                        startUpdateTimer()
                                        RxBus.send(EventObjectivesUpdateGui())
                                    } else {
                                        ToastUtils.showToastInUiThread(context, R.string.requirementnotmet)
                                    }
                                } else {
                                    ToastUtils.showToastInUiThread(context, R.string.failedretrievetime)
                                }
                            }
                        }
                    }, NetworkChangeReceiver.isConnected())
            }
            holder.start.setOnClickListener {
                holder.start.visibility = View.INVISIBLE
                NetworkChangeReceiver.fetch()
                if (objectives_fake.isChecked) {
                    objective.startedOn = DateUtil.now()
                    scrollToCurrentObjective()
                    startUpdateTimer()
                    RxBus.send(EventObjectivesUpdateGui())
                } else
                    SntpClient.ntpTime(object : SntpClient.Callback() {
                        override fun run() {
                            activity?.runOnUiThread {
                                holder.start.visibility = View.VISIBLE
                                log.debug("NTP time: $time System time: ${DateUtil.now()}")
                                if (!networkConnected) {
                                    ToastUtils.showToastInUiThread(context, R.string.notconnected)
                                } else if (success) {
                                    objective.startedOn = time
                                    scrollToCurrentObjective()
                                    startUpdateTimer()
                                    RxBus.send(EventObjectivesUpdateGui())
                                } else {
                                    ToastUtils.showToastInUiThread(context, R.string.failedretrievetime)
                                }
                            }
                        }
                    }, NetworkChangeReceiver.isConnected())
            }
            holder.revert.setOnClickListener {
                objective.accomplishedOn = 0
                objective.startedOn = 0
                if (position > 0) {
                    val prevObj = ObjectivesPlugin.objectives[position - 1]
                    prevObj.accomplishedOn = 0
                }
                scrollToCurrentObjective()
                RxBus.send(EventObjectivesUpdateGui())
            }
            if (objective.hasSpecialInput && !objective.isAccomplished && objective.isStarted) {
                // generate random request code if none exists
                val request = SP.getString(R.string.key_objectives_request_code, String.format("%1$05d", (Math.random() * 99999).toInt()))
                SP.putString(R.string.key_objectives_request_code, request)
                holder.requestCode.text = MainApp.gs(R.string.requestcode, request)
                holder.requestCode.visibility = View.VISIBLE
                holder.enterButton.visibility = View.VISIBLE
                holder.input.visibility = View.VISIBLE
                holder.inputHint.visibility = View.VISIBLE
                holder.enterButton.setOnClickListener {
                    val input = holder.input.text.toString()
                    objective.specialAction(activity, input)
                    RxBus.send(EventObjectivesUpdateGui())
                }
            } else {
                holder.enterButton.visibility = View.GONE
                holder.input.visibility = View.GONE
                holder.inputHint.visibility = View.GONE
                holder.requestCode.visibility = View.GONE
            }
        }


        override fun getItemCount(): Int {
            return ObjectivesPlugin.objectives.size
        }

        inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.objective_title)
            val objective: TextView = itemView.findViewById(R.id.objective_objective)
            val gate: TextView = itemView.findViewById(R.id.objective_gate)
            val accomplished: TextView = itemView.findViewById(R.id.objective_accomplished)
            val progress: LinearLayout = itemView.findViewById(R.id.objective_progress)
            val verify: Button = itemView.findViewById(R.id.objective_verify)
            val start: Button = itemView.findViewById(R.id.objective_start)
            val revert: Button = itemView.findViewById(R.id.objective_back)
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
