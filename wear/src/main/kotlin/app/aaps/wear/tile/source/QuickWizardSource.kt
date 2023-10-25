package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.TileSource
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickWizardSource @Inject constructor(private val context: Context, private val sp: SP, private val aapsLogger: AAPSLogger) : TileSource {

    override fun getSelectedActions(): List<Action> {
        val quickList = mutableListOf<Action>()
        val quickMap = getQuickWizardData(sp)
        val sfm = secondsFromMidnight()

        for (quick in quickMap.entries) {
            val isActive = sfm in quick.validFrom..quick.validTo
            if (isActive && quick.guid.isNotEmpty()) {
                quickList.add(
                    Action(
                        buttonText = quick.buttonText,
                        buttonTextSub = "${quick.carbs} g",
                        iconRes = R.drawable.ic_quick_wizard,
                        activityClass = BackgroundActionActivity::class.java.name,
                        action = EventData.ActionQuickWizardPreCheck(quick.guid),
                        message = context.resources.getString(R.string.action_quick_wizard_confirmation)
                    )
                )
                aapsLogger.info(LTag.WEAR, """getSelectedActions: active ${quick.buttonText} guid=${quick.guid}""")
            } else {
                aapsLogger.info(LTag.WEAR, """getSelectedActions: not active ${quick.buttonText} guid=${quick.guid}""")
            }
        }
        return quickList
    }

    override fun getValidFor(): Long? {
        val quickMap = getQuickWizardData(sp)
        if (quickMap.entries.size == 0) return null

        val sfm = secondsFromMidnight()
        var validTill = 24 * 60 * 60

        for (quick in quickMap.entries) {
            val isActive = sfm in quick.validFrom..quick.validTo
            if (quick.guid.isNotEmpty()) {
                if (isActive && validTill > quick.validTo) validTill = quick.validTo
                if (quick.validFrom in (sfm + 1) until validTill) validTill = quick.validFrom
            }
        }

        val validWithin = 60
        //aapsLogger.info(LTag.WEAR, "getValidTill: sfm$sfm till$validTill d=$delta")
        return (validTill - sfm + validWithin) * 1000L
    }

    private fun getQuickWizardData(sp: SP): EventData.QuickWizard =
        EventData.deserialize(sp.getString(R.string.key_quick_wizard_data, EventData.QuickWizard(arrayListOf()).serialize())) as EventData.QuickWizard

    private fun secondsFromMidnight(): Int {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val passed: Long = System.currentTimeMillis() - c.timeInMillis

        return (passed / 1000).toInt()
    }

    override fun getResourceReferences(resources: Resources): List<Int> = listOf(R.drawable.ic_quick_wizard)
}
