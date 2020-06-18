package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationAddAction
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import kotlinx.android.synthetic.main.automation_dialog_choose_action.*
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

class ChooseActionDialog : DialogFragmentWithDate() {
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var mainApp : MainApp

    private var checkedIndex = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // restore checked radio button
        savedInstanceState?.let { bundle ->
            checkedIndex = bundle.getInt("checkedIndex")
        }

        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_choose_action, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (a in automationPlugin.getActionDummyObjects()) {
            val radioButton = RadioButton(context)
            radioButton.setText(a.friendlyName())
            radioButton.tag = a.javaClass.name
            automation_radioGroup.addView(radioButton)
        }

        if (checkedIndex != -1)
            (automation_radioGroup.getChildAt(checkedIndex) as RadioButton).isChecked = true
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
            clazz.primaryConstructor?.call(mainApp) as Action
        }
    }

    private fun getActionClass(): String? {
        val radioButtonID = automation_radioGroup.checkedRadioButtonId
        val radioButton = automation_radioGroup.findViewById<RadioButton>(radioButtonID)
        return radioButton?.let {
            it.tag as String
        }
    }

    private fun determineCheckedIndex(): Int {
        for (i in 0 until automation_radioGroup.childCount) {
            if ((automation_radioGroup.getChildAt(i) as RadioButton).isChecked)
                return i
        }
        return -1
    }
}
