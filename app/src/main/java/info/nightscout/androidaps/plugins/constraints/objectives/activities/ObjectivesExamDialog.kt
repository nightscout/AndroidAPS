package info.nightscout.androidaps.plugins.constraints.objectives.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import kotlinx.android.synthetic.main.objectives_exam_fragment.*

class ObjectivesExamDialog : DialogFragment() {
    companion object {
        var objective: Objective? = null
    }

    var currentTask = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            currentTask = bundle.getInt("currentTask", 0)
        }

        return inflater.inflate(R.layout.objectives_exam_fragment, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    override fun onResume() {
        super.onResume()
        updateGui()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putInt("currentTask", currentTask)
    }

    fun updateGui() {
        objective?.let { objective ->
            val task: ExamTask = objective.tasks[currentTask] as ExamTask
            objectives_exam_name.setText(task.task)
            objectives_exam_question.setText(task.question)
            // Options
            objectives_exam_options.removeAllViews()
            task.options.forEach {
                val cb = it.generate(context)
                if (task.answered) {
                    cb.isEnabled = false
                    if (it.isCorrect)
                        cb.isChecked = true
                }
                objectives_exam_options.addView(cb)
            }
            // Hints
            objectives_exam_hints.removeAllViews()
            for (h in task.hints) {
                objectives_exam_hints.addView(h.generate(context))
            }
            // Disabled to
            objectives_exam_disabledto.text = MainApp.gs(R.string.answerdisabledto, DateUtil.timeString(task.disabledTo))
            objectives_exam_disabledto.visibility = if (task.isEnabledAnswer) View.GONE else View.VISIBLE
            // Buttons
            objectives_exam_verify.isEnabled = !task.answered && task.isEnabledAnswer
            objectives_exam_verify.setOnClickListener {
                var result = true
                for (o in task.options) {
                    val option: Option = o as Option;
                    result = result && option.evaluate()
                }
                task.setAnswered(result);
                if (!result) {
                    task.disabledTo = DateUtil.now() + T.hours(1).msecs()
                    ToastUtils.showToastInUiThread(context, R.string.wronganswer)
                } else task.disabledTo = 0
                updateGui()
                RxBus.send(EventObjectivesUpdateGui())
            }
            close.setOnClickListener { dismiss() }
            objectives_exam_reset.setOnClickListener {
                task.answered = false
                //task.disabledTo = 0
                updateGui()
                RxBus.send(EventObjectivesUpdateGui())
            }
            objectives_back_button.isEnabled = currentTask != 0
            objectives_back_button.setOnClickListener {
                currentTask--
                updateGui()
            }
            objectives_next_button.isEnabled = currentTask != objective.tasks.size - 1
            objectives_next_button.setOnClickListener {
                currentTask++
                updateGui()
            }

            objectives_next_unanswered_button.isEnabled = !objective.isCompleted
            objectives_next_unanswered_button.setOnClickListener {
                for (i in (currentTask + 1)..(objective.tasks.size - 1)) {
                    if (!objective.tasks[i].isCompleted) {
                        currentTask = i
                        updateGui()
                        return@setOnClickListener
                    }
                }
                for (i in 0..currentTask) {
                    if (!objective.tasks[i].isCompleted) {
                        currentTask = i
                        updateGui()
                        return@setOnClickListener
                    }
                }
            }
        }
    }
}
