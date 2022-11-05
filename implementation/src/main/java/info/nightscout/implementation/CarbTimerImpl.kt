package info.nightscout.implementation

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.CarbTimer
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.TimerUtil
import info.nightscout.automation.AutomationEvent
import info.nightscout.automation.AutomationPlugin
import info.nightscout.automation.actions.ActionAlarm
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDelta
import info.nightscout.automation.triggers.TriggerBg
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerDelta
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarbTimerImpl @Inject constructor(
    private val injector: HasAndroidInjector,
    private val rh: ResourceHelper,
    private val automationPlugin: AutomationPlugin,
    private val timerUtil: TimerUtil
) : CarbTimer {

    /**
     * Generate reminder via [info.nightscout.androidaps.utils.TimerUtil]
     *
     * @param seconds seconds to the future
     */
    override fun scheduleTimeToEatReminder(seconds: Int) =
        timerUtil.scheduleReminder(seconds, rh.gs(R.string.time_to_eat))

    /**
     * Create new Automation event to alarm when is time to eat
     */
    override fun scheduleAutomationEventEatReminder() {
        val event = AutomationEvent(injector).apply {
            title = rh.gs(R.string.bolus_advisor)
            readOnly = true
            systemAction = true
            autoRemove = true
            trigger = TriggerConnector(injector, TriggerConnector.Type.OR).apply {

                // Bg under 180 mgdl and dropping by 15 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 180.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(rh, -15.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(
                        TriggerDelta(
                            injector,
                            InputDelta(rh, -8.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE),
                            GlucoseUnit.MGDL,
                            Comparator.Compare.IS_EQUAL_OR_LESSER
                        )
                    )
                })
                // Bg under 160 mgdl and dropping by 9 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 160.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(rh, -9.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(
                        TriggerDelta(
                            injector,
                            InputDelta(rh, -5.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE),
                            GlucoseUnit.MGDL,
                            Comparator.Compare.IS_EQUAL_OR_LESSER
                        )
                    )
                })
                // Bg under 145 mgdl and dropping
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 145.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(rh, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(
                        TriggerDelta(
                            injector,
                            InputDelta(rh, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE),
                            GlucoseUnit.MGDL,
                            Comparator.Compare.IS_EQUAL_OR_LESSER
                        )
                    )
                })
            }
            actions.add(ActionAlarm(injector, rh.gs(R.string.time_to_eat)))
        }

        automationPlugin.addIfNotExists(event)
    }

    /**
     * Remove Automation event
     */
    override fun removeAutomationEventEatReminder() {
        val event = AutomationEvent(injector).apply {
            title = rh.gs(R.string.bolus_advisor)
        }
        automationPlugin.removeIfExists(event)
    }
}