package info.nightscout.androidaps.utils

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.actions.ActionAlarm
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDelta
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBg
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDelta
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BolusTimer @Inject constructor(
    private val injector: HasAndroidInjector,
    private val rh: ResourceHelper,
    private val automationPlugin: AutomationPlugin,
) {

    fun scheduleBolusReminder() {
        val event = AutomationEvent(injector).apply {
            title = rh.gs(R.string.bolusreminder)
            readOnly = true
            systemAction = true
            autoRemove = true
            trigger = TriggerConnector(injector, TriggerConnector.Type.AND).apply {

                // Bg above 70 mgdl and delta positive mgdl
                list.add(TriggerBg(injector, 70.0, GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_GREATER))
                list.add(
                    TriggerDelta(
                        injector, InputDelta(rh, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare
                            .IS_GREATER
                    )
                )
            }
            actions.add(ActionAlarm(injector, rh.gs(R.string.time_to_bolus)))
        }

        automationPlugin.addIfNotExists(event)
    }

    fun removeBolusReminder() {
        val event = AutomationEvent(injector).apply {
            title = rh.gs(R.string.bolusreminder)
        }
        automationPlugin.removeIfExists(event)
    }
}