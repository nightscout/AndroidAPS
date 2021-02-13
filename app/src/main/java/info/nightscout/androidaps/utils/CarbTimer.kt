package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
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
    private val context: Context,
    private val injector: HasAndroidInjector,
    private val resourceHelper: ResourceHelper,
    private val automationPlugin: AutomationPlugin
) {

    fun scheduleReminder(time: Long) = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        val length: Int = ((time - DateUtil.now()) / 1000).toInt()
        flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra(AlarmClock.EXTRA_LENGTH, length)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        putExtra(AlarmClock.EXTRA_MESSAGE, resourceHelper.gs(R.string.timetoeat))
        context.startActivity(this)
    }

    fun scheduleEatReminder() {
        val event = AutomationEvent(injector).apply {
            title = resourceHelper.gs(R.string.bolusadvisor)
            readOnly = true
            systemAction = true
            autoRemove = true
            trigger = TriggerConnector(injector, TriggerConnector.Type.OR).apply {

                // Bg under 180 mgdl and dropping by 15 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 180.0, Constants.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(injector, -15.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), Constants.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(injector, -8.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE), Constants.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                })
                // Bg under 160 mgdl and dropping by 9 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 160.0, Constants.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(injector, -9.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), Constants.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(injector, -5.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE), Constants.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                })
                // Bg under 145 mgdl and dropping
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 145.0, Constants.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(injector, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), Constants.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(injector, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE), Constants.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                })
            }
            actions.add(ActionAlarm(injector, resourceHelper.gs(R.string.time_to_eat)))
        }

        automationPlugin.addIfNotExists(event)
    }

}