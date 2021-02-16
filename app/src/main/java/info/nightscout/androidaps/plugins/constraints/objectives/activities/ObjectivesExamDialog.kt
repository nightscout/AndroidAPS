package info.nightscout.androidaps.plugins.constraints.objectives.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ObjectivesExamFragmentBinding
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective.Option
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ObjectivesExamDialog : DaggerDialogFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
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
            binding.examDisabledto.text = resourceHelper.gs(R.string.answerdisabledto, dateUtil.timeString(task.disabledTo))
            binding.examDisabledto.visibility = if (task.isEnabledAnswer()) View.GONE else View.VISIBLE
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
                    task.disabledTo = DateUtil.now() + T.hours(1).msecs()
                    ToastUtils.showToastInUiThread(context, R.string.wronganswer)
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
