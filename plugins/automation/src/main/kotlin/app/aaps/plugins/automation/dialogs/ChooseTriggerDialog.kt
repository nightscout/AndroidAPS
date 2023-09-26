package app.aaps.plugins.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.automation.databinding.AutomationDialogChooseTriggerBinding
import app.aaps.plugins.automation.triggers.Trigger
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

class ChooseTriggerDialog : BaseDialog() {

    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var injector: HasAndroidInjector

    private var checkedIndex = -1
    private var clickListener: OnClickListener? = null

    private var _binding: AutomationDialogChooseTriggerBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    interface OnClickListener {

        fun onClick(newTriggerObject: Trigger)
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
        _binding = AutomationDialogChooseTriggerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (t in automationPlugin.getTriggerDummyObjects()) {
            val radioButton = RadioButton(context)
            radioButton.setText(t.friendlyName())
            radioButton.tag = t.javaClass.name
            binding.chooseTriggerRadioGroup.addView(radioButton)
        }

        if (checkedIndex != -1)
            (binding.chooseTriggerRadioGroup.getChildAt(checkedIndex) as RadioButton).isChecked = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        instantiateTrigger()?.let {
            clickListener?.onClick(it)
        }
        return true
    }

    fun setOnClickListener(clickListener: OnClickListener) {
        this.clickListener = clickListener
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("checkedIndex", determineCheckedIndex())
    }

    private fun instantiateTrigger(): Trigger? {
        return getTriggerClass()?.let {
            val clazz = Class.forName(it).kotlin
            clazz.primaryConstructor?.call(injector) as Trigger
        }
    }

    private fun getTriggerClass(): String? {
        val radioButtonID = binding.chooseTriggerRadioGroup.checkedRadioButtonId
        val radioButton = binding.chooseTriggerRadioGroup.findViewById<RadioButton>(radioButtonID)
        return radioButton?.let {
            it.tag as String
        }
    }

    private fun determineCheckedIndex(): Int {
        for (i in 0 until binding.chooseTriggerRadioGroup.childCount) {
            if ((binding.chooseTriggerRadioGroup.getChildAt(i) as RadioButton).isChecked)
                return i
        }
        return -1
    }
}
