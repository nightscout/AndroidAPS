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

    companion object {
        const val COOLDOWN_MILLIS = 3_600_000L // 1 hour
    }

    override fun getSelectedActions(): List<Action> {
        val quickList = mutableListOf<Action>()
        val quickMap = getQuickWizardData(sp)
        val sfm = secondsFromMidnight()
        val now = System.currentTimeMillis()

        for (quick in quickMap.entries) {
            val isActive = sfm in quick.validFrom..quick.validTo && now - quick.lastUsed > COOLDOWN_MILLIS
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
        if (quickMap.entries.isEmpty()) return null

        val sfm = secondsFromMidnight()
        val now = System.currentTimeMillis()
        var validTill = 24 * 60 * 60

        for (quick in quickMap.entries) {
            val onCooldown = now - quick.lastUsed <= COOLDOWN_MILLIS
            val isActive = sfm in quick.validFrom..quick.validTo && !onCooldown
            if (quick.guid.isNotEmpty()) {
                if (isActive && validTill > quick.validTo) validTill = quick.validTo
                if (quick.validFrom in (sfm + 1) until validTill) validTill = quick.validFrom
                // If entry is on cooldown but within time window, refresh when cooldown expires
                if (onCooldown && sfm in quick.validFrom..quick.validTo) {
                    val cooldownRemainingSecs = ((quick.lastUsed + COOLDOWN_MILLIS - now) / 1000).toInt()
                    val cooldownExpirySfm = sfm + cooldownRemainingSecs
                    if (cooldownExpirySfm in (sfm + 1) until validTill) validTill = cooldownExpirySfm
                }
            }
        }

        val validWithin = 60
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
