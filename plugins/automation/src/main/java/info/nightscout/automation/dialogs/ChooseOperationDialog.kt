package info.nightscout.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import info.nightscout.automation.databinding.AutomationDialogChooseOperationBinding
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class ChooseOperationDialog : BaseDialog() {

    @Inject lateinit var rh: ResourceHelper

    private var checkedIndex = -1

    private var _binding: AutomationDialogChooseOperationBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    abstract class Callback : Runnable {

        var result: Int? = null
        fun result(result: Int?): Callback {
            this.result = result
            return this
        }
    }

    private var callback: Callback? = null

    fun setCallback(callback: Callback): ChooseOperationDialog {
        this.callback = callback
        return this
    }

    fun setCheckedIndex(checkedIndex: Int): ChooseOperationDialog {
        this.checkedIndex = checkedIndex
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // restore checked radio button
        savedInstanceState?.let { bundle ->
            checkedIndex = bundle.getInt("checkedIndex")
        }

        onCreateViewGeneral()
        _binding = AutomationDialogChooseOperationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (t in TriggerConnector.Type.labels(rh)) {
            val radioButton = RadioButton(context)
            radioButton.text = t
            binding.chooseOperationRadioGroup.addView(radioButton)
        }

        if (checkedIndex != -1)
            (binding.chooseOperationRadioGroup.getChildAt(checkedIndex) as RadioButton).isChecked = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        callback?.result(determineCheckedIndex())?.run()
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("checkedIndex", determineCheckedIndex())
    }

    private fun determineCheckedIndex(): Int {
        for (i in 0 until binding.chooseOperationRadioGroup.childCount) {
            if ((binding.chooseOperationRadioGroup.getChildAt(i) as RadioButton).isChecked)
                return i
        }
        return -1
    }
}
