package info.nightscout.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import dagger.android.HasAndroidInjector
import info.nightscout.automation.AutomationPlugin
import info.nightscout.automation.actions.Action
import info.nightscout.automation.databinding.AutomationDialogChooseActionBinding
import info.nightscout.automation.events.EventAutomationAddAction
import info.nightscout.automation.events.EventAutomationUpdateGui
import info.nightscout.rx.bus.RxBus
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

class ChooseActionDialog : BaseDialog() {

    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var injector: HasAndroidInjector

    private var checkedIndex = -1

    private var _binding: AutomationDialogChooseActionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // restore checked radio button
        savedInstanceState?.let { bundle ->
            checkedIndex = bundle.getInt("checkedIndex")
        }

        onCreateViewGeneral()
        _binding = AutomationDialogChooseActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (a in automationPlugin.getActionDummyObjects()) {
            val radioButton = RadioButton(context)
            radioButton.setText(a.friendlyName())
            radioButton.tag = a.javaClass.name
            binding.radioGroup.addView(radioButton)
        }

        if (checkedIndex != -1)
            (binding.radioGroup.getChildAt(checkedIndex) as RadioButton).isChecked = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        instantiateAction()?.let {
            rxBus.send(EventAutomationAddAction(it))
            rxBus.send(EventAutomationUpdateGui())
        }
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("checkedIndex", determineCheckedIndex())
    }

    private fun instantiateAction(): Action? {
        return getActionClass()?.let {
            val clazz = Class.forName(it).kotlin
            clazz.primaryConstructor?.call(injector) as Action
        }
    }

    private fun getActionClass(): String? {
        val radioButtonID = binding.radioGroup.checkedRadioButtonId
        val radioButton = binding.radioGroup.findViewById<RadioButton>(radioButtonID)
        return radioButton?.let {
            it.tag as String
        }
    }

    private fun determineCheckedIndex(): Int {
        for (i in 0 until binding.radioGroup.childCount) {
            if ((binding.radioGroup.getChildAt(i) as RadioButton).isChecked)
                return i
        }
        return -1
    }
}
