package app.aaps.plugins.constraints.objectives

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNtpStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.databinding.ObjectivesFragmentBinding
import app.aaps.plugins.constraints.databinding.ObjectivesItemBinding
import app.aaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import app.aaps.plugins.constraints.objectives.dialogs.NtpProgressDialog
import app.aaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import app.aaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import app.aaps.plugins.constraints.objectives.objectives.Objective.UITask
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ObjectivesFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sntpClient: SntpClient
    @Inject lateinit var uel: UserEntryLogger

    private val objectivesAdapter = ObjectivesAdapter()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val objectiveUpdater = object : Runnable {
        override fun run() {
            handler.postDelayed(this, (60 * 1000).toLong())
            updateGUI()
        }
    }

    private var _binding: ObjectivesFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ObjectivesFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = objectivesAdapter
        binding.fake.setOnClickListener { updateGUI() }
        binding.reset.setOnClickListener {
            objectivesPlugin.reset()
            updateGUI()
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
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                        binding.recyclerview.layoutManager?.startSmoothScroll(smoothScroller)
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

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val objective = objectivesPlugin.objectives[position]
            holder.binding.title.text = rh.gs(R.string.nth_objective, position + 1)
            if (objective.objective != 0) {
                holder.binding.objective.visibility = View.VISIBLE
                holder.binding.objective.text = rh.gs(objective.objective)
            } else holder.binding.objective.visibility = View.GONE

            if (objective.gate != 0) {
                holder.binding.gate.visibility = View.VISIBLE
                holder.binding.gate.text = rh.gs(objective.gate)
            } else holder.binding.gate.visibility = View.GONE

            if (!objective.isStarted) {
                holder.binding.gate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                holder.binding.verify.visibility = View.GONE
                holder.binding.progress.visibility = View.GONE
                holder.binding.accomplished.visibility = View.GONE
                holder.binding.unfinish.visibility = View.GONE
                holder.binding.unstart.visibility = View.GONE
                holder.binding.learnedLabel.visibility = View.GONE
                holder.binding.learned.removeAllViews()
                if (position == 0 || objectivesPlugin.allPriorAccomplished(position))
                    holder.binding.start.visibility = View.VISIBLE
                else
                    holder.binding.start.visibility = View.GONE
            } else if (objective.isAccomplished) {
                holder.binding.gate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.isAccomplishedColor))
                holder.binding.verify.visibility = View.GONE
                holder.binding.progress.visibility = View.GONE
                holder.binding.start.visibility = View.GONE
                holder.binding.accomplished.visibility = View.VISIBLE
                holder.binding.unfinish.visibility = View.VISIBLE
                holder.binding.unstart.visibility = View.GONE
                holder.binding.learnedLabel.visibility = View.VISIBLE
                holder.binding.learned.removeAllViews()
                for (task in objective.tasks) {
                    if (task.shouldBeIgnored()) continue
                    for (learned in task.learned) {
                        holder.binding.learned.addView(TextView(context).also { it.text = rh.gs(learned.learned) + "\n" })
                    }
                }
            } else if (objective.isStarted) {
                holder.binding.gate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                holder.binding.verify.visibility = View.VISIBLE
                holder.binding.verify.isEnabled = objective.isCompleted || binding.fake.isChecked
                holder.binding.start.visibility = View.GONE
                holder.binding.accomplished.visibility = View.GONE
                holder.binding.unfinish.visibility = View.GONE
                holder.binding.unstart.visibility = View.VISIBLE
                holder.binding.progress.visibility = View.VISIBLE
                holder.binding.progress.removeAllViews()
                holder.binding.learnedLabel.visibility = View.GONE
                holder.binding.learned.removeAllViews()
                for (task in objective.tasks) {
                    if (task.shouldBeIgnored()) continue
                    // name
                    val name = TextView(holder.binding.progress.context)
                    name.text = "${rh.gs(task.task)}:"
                    name.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                    holder.binding.progress.addView(name, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    // hint
                    task.hints.forEach { h ->
                        if (!task.isCompleted())
                            context?.let { holder.binding.progress.addView(h.generate(it)) }
                    }
                    // state
                    val state = TextView(holder.binding.progress.context)
                    state.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                    val basicHTML = "<font color=\"%1\$s\"><b>%2\$s</b></font>"
                    val formattedHTML =
                        String.format(
                            basicHTML,
                            if (task.isCompleted()) rh.gac(context, app.aaps.core.ui.R.attr.isCompletedColor) else rh.gac(context, app.aaps.core.ui.R.attr.isNotCompletedColor),
                            task.progress
                        )
                    state.text = HtmlHelper.fromHtml(formattedHTML)
                    state.gravity = Gravity.END
                    holder.binding.progress.addView(state, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
                    if (task is UITask) {
                        state.setOnClickListener { task.code.invoke(this@ObjectivesFragment.requireContext(), task) { updateGUI() } }
                    }
                    if (task.isCompleted()) {
                        if (task.learned.isNotEmpty())
                            holder.binding.progress.addView(
                                TextView(context).also {
                                    it.text = rh.gs(R.string.what_i_ve_learned)
                                    it.setTypeface(it.typeface, Typeface.BOLD)
                                })
                        for (learned in task.learned)
                            holder.binding.progress.addView(TextView(context).also { it.text = rh.gs(learned.learned) })
                    }
                    // horizontal line
                    val separator = View(holder.binding.progress.context)
                    separator.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.separatorColor))
                    holder.binding.progress.addView(separator, LinearLayout.LayoutParams.MATCH_PARENT, 2)
                }
            }
            holder.binding.accomplished.text = rh.gs(R.string.accomplished, dateUtil.dateAndTimeString(objective.accomplishedOn))
            holder.binding.accomplished.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            holder.binding.verify.setOnClickListener {
                receiverStatusStore.updateNetworkStatus()
                if (binding.fake.isChecked) {
                    objective.accomplishedOn = dateUtil.now()
                    scrollToCurrentObjective()
                    startUpdateTimer()
                    rxBus.send(EventObjectivesUpdateGui())
                    rxBus.send(EventSWUpdate(false))
                } else {
                    // move out of UI thread
                    handler.post {
                        NtpProgressDialog().show((context as AppCompatActivity).supportFragmentManager, "NtpCheck")
                        rxBus.send(EventNtpStatus(rh.gs(app.aaps.core.ui.R.string.timedetection), 0))
                        sntpClient.ntpTime(object : SntpClient.Callback() {
                            override fun run() {
                                aapsLogger.debug("NTP time: $time System time: ${dateUtil.now()}")
                                SystemClock.sleep(300)
                                if (!networkConnected) {
                                    rxBus.send(EventNtpStatus(rh.gs(R.string.notconnected), 99))
                                } else if (success) {
                                    if (objective.isCompleted(time)) {
                                        objective.accomplishedOn = time
                                        rxBus.send(EventNtpStatus(rh.gs(app.aaps.core.ui.R.string.success), 100))
                                        SystemClock.sleep(1000)
                                        rxBus.send(EventObjectivesUpdateGui())
                                        rxBus.send(EventSWUpdate(false))
                                        SystemClock.sleep(100)
                                        scrollToCurrentObjective()
                                    } else {
                                        rxBus.send(EventNtpStatus(rh.gs(R.string.requirementnotmet), 99))
                                    }
                                } else {
                                    rxBus.send(EventNtpStatus(rh.gs(R.string.failedretrievetime), 99))
                                }
                            }
                        }, receiverStatusStore.isKnownNetworkStatus && receiverStatusStore.isConnected)
                    }
                }
            }
            holder.binding.start.setOnClickListener {
                receiverStatusStore.updateNetworkStatus()
                if (binding.fake.isChecked) {
                    objective.startedOn = dateUtil.now()
                    scrollToCurrentObjective()
                    startUpdateTimer()
                    rxBus.send(EventObjectivesUpdateGui())
                    rxBus.send(EventSWUpdate(false))
                } else
                // move out of UI thread
                    handler.post {
                        NtpProgressDialog().show((context as AppCompatActivity).supportFragmentManager, "NtpCheck")
                        rxBus.send(EventNtpStatus(rh.gs(app.aaps.core.ui.R.string.timedetection), 0))
                        sntpClient.ntpTime(object : SntpClient.Callback() {
                            override fun run() {
                                aapsLogger.debug("NTP time: $time System time: ${dateUtil.now()}")
                                SystemClock.sleep(300)
                                if (!networkConnected) {
                                    rxBus.send(EventNtpStatus(rh.gs(R.string.notconnected), 99))
                                } else if (success) {
                                    objective.startedOn = time
                                    rxBus.send(EventNtpStatus(rh.gs(app.aaps.core.ui.R.string.success), 100))
                                    SystemClock.sleep(1000)
                                    rxBus.send(EventObjectivesUpdateGui())
                                    rxBus.send(EventSWUpdate(false))
                                    SystemClock.sleep(100)
                                    scrollToCurrentObjective()
                                } else {
                                    rxBus.send(EventNtpStatus(rh.gs(R.string.failedretrievetime), 99))
                                }
                            }
                        }, receiverStatusStore.isKnownNetworkStatus && receiverStatusStore.isConnected)
                    }
            }
            holder.binding.unstart.setOnClickListener {
                activity?.let { activity ->
                    OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.objectives), rh.gs(R.string.doyouwantresetstart), Runnable {
                        uel.log(
                            action = Action.OBJECTIVE_UNSTARTED,
                            source = Sources.Objectives,
                            value = ValueWithUnit.SimpleInt(position + 1)
                        )
                        objective.startedOn = 0
                        scrollToCurrentObjective()
                        rxBus.send(EventObjectivesUpdateGui())
                        rxBus.send(EventSWUpdate(false))
                    })
                }
            }
            holder.binding.unfinish.setOnClickListener {
                objective.accomplishedOn = 0
                scrollToCurrentObjective()
                rxBus.send(EventObjectivesUpdateGui())
                rxBus.send(EventSWUpdate(false))
            }
        }

        override fun getItemCount(): Int {
            return objectivesPlugin.objectives.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = ObjectivesItemBinding.bind(itemView)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateGUI() {
        activity?.runOnUiThread { objectivesAdapter.notifyDataSetChanged() }
    }
}
