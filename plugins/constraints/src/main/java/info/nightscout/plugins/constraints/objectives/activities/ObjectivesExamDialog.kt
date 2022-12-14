package info.nightscout.plugins.constraints.objectives.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerDialogFragment
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.plugins.constraints.R
import info.nightscout.plugins.constraints.databinding.ObjectivesExamFragmentBinding
import info.nightscout.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import info.nightscout.plugins.constraints.objectives.objectives.Objective
import info.nightscout.plugins.constraints.objectives.objectives.Objective.ExamTask
import info.nightscout.plugins.constraints.objectives.objectives.Objective.Option
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject

class ObjectivesExamDialog : DaggerDialogFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    companion object {

        var objective: Objective? = null
    }

    private var currentTask = 0

    private var _binding: ObjectivesExamFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            currentTask = bundle.getInt("currentTask", 0)
        }

        _binding = ObjectivesExamFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        updateGui()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putInt("currentTask", currentTask)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    fun updateGui() {
        if (_binding == null) return
        objective?.let { objective ->
            val task: ExamTask = objective.tasks[currentTask] as ExamTask
            binding.examName.setText(task.task)
            binding.examQuestion.setText(task.question)
            // Options
            binding.examOptions.removeAllViews()
            task.options.forEach {
                context?.let { context ->
                    val cb = it.generate(context)
                    if (task.answered) {
                        cb.isEnabled = false
                        if (it.isCorrect)
                            cb.isChecked = true
                    }
                    binding.examOptions.addView(cb)
                }
            }
            // Hints
            binding.examHints.removeAllViews()
            for (h in task.hints) {
                context?.let { binding.examHints.addView(h.generate(it)) }
            }
            // Disabled to
            binding.examDisabledTo.text = rh.gs(R.string.answerdisabledto, dateUtil.timeString(task.disabledTo))
            binding.examDisabledTo.visibility = if (task.isEnabledAnswer()) View.GONE else View.VISIBLE
            // Buttons
            binding.examVerify.isEnabled = !task.answered && task.isEnabledAnswer()
            binding.examVerify.setOnClickListener {
                var result = true
                for (o in task.options) {
                    val option: Option = o
                    result = result && option.evaluate()
                }
                task.answered = result
                if (!result) {
                    task.disabledTo = dateUtil.now() + T.hours(1).msecs()
                    context?.let { it1 -> ToastUtils.infoToast(it1, R.string.wronganswer) }
                } else task.disabledTo = 0
                updateGui()
                rxBus.send(EventObjectivesUpdateGui())
            }
            binding.close.setOnClickListener { dismiss() }
            binding.examReset.setOnClickListener {
                task.answered = false
                //task.disabledTo = 0
                updateGui()
                rxBus.send(EventObjectivesUpdateGui())
            }
            binding.backButton.isEnabled = currentTask != 0
            binding.backButton.setOnClickListener {
                currentTask--
                updateGui()
            }
            binding.nextButton.isEnabled = currentTask != objective.tasks.size - 1
            binding.nextButton.setOnClickListener {
                currentTask++
                updateGui()
            }

            binding.nextUnansweredButton.isEnabled = !objective.isCompleted
            binding.nextUnansweredButton.setOnClickListener {
                for (i in (currentTask + 1) until objective.tasks.size) {
                    if (!objective.tasks[i].isCompleted()) {
                        currentTask = i
                        updateGui()
                        return@setOnClickListener
                    }
                }
                for (i in 0..currentTask) {
                    if (!objective.tasks[i].isCompleted()) {
                        currentTask = i
                        updateGui()
                        return@setOnClickListener
                    }
                }
            }
        }
    }
}
