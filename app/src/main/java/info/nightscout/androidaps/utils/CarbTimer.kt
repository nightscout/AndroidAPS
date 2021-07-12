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
class CarbTimer @Inject constructor(
    private val injector: HasAndroidInjector,
    private val resourceHelper: ResourceHelper,
    private val automationPlugin: AutomationPlugin,
    private val timerUtil: TimerUtil
) {

    fun scheduleReminder(time: Long, text: String? = null) =
        timerUtil.scheduleReminder(time, text ?: resourceHelper.gs(R.string.timetoeat))

    fun scheduleEatReminder() {
        val event = AutomationEvent(injector).apply {
            title = resourceHelper.gs(R.string.bolusadvisor)
            readOnly = true
            systemAction = true
            autoRemove = true
            trigger = TriggerConnector(injector, TriggerConnector.Type.OR).apply {

                // Bg under 180 mgdl and dropping by 15 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 180.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(resourceHelper, -15.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(resourceHelper, -8.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                })
                // Bg under 160 mgdl and dropping by 9 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 160.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(resourceHelper, -9.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(resourceHelper, -5.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                })
                // Bg under 145 mgdl and dropping
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 145.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(resourceHelper, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(resourceHelper, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                })
            }
            actions.add(ActionAlarm(injector, resourceHelper.gs(R.string.time_to_eat)))
        }

        automationPlugin.addIfNotExists(event)
    }

}