package info.nightscout.androidaps.plugins.constraints.objectives.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective.*
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

        dialog.setCanceledOnTouchOutside(false)
        return inflater.inflate(R.layout.objectives_exam_fragment, container, false)
    }

    override fun onResume() {
        super.onResume()
        updateGui()
    }

    fun updateGui() {
        objective?.let { objective ->
            val task: ExamTask = objective.tasks[currentTask] as ExamTask
            objectives_exam_name.setText(task.task)
            objectives_exam_question.setText(task.question)
            objectives_exam_options.removeAllViews()
            for (o in task.options) {
                val option: Option = o as Option;
                val cb = option.generate(context)
                if (task.answered) {
                    cb.isEnabled = false
                    if (option.isCorrect)
                        cb.isChecked = true
                }
                objectives_exam_options.addView(cb)
            }
            objectives_exam_hints.removeAllViews()
            for (h in task.hints) {
                val hint: Hint = h as Hint;
                objectives_exam_hints.addView(hint.generate(context))
            }
            objectives_exam_verify.setOnClickListener {
                var result = true
                for (o in task.options) {
                    val option: Option = o as Option;
                    result = result && option.evaluate()
                }
                task.setAnswered(result);
                updateGui()
            }
            cancel.setOnClickListener { dismiss() }
            objectives_exam_reset.setOnClickListener {
                task.answered = false
                updateGui()
            }
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putInt("currentTask", currentTask)
    }
}
