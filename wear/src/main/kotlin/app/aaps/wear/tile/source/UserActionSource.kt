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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserActionSource @Inject constructor(private val context: Context, private val sp: SP, private val aapsLogger: AAPSLogger) : TileSource {

    override fun getSelectedActions(): List<Action> {
        val userList = mutableListOf<Action>()
        val userMap = getUserActionData(sp)

        for (useraction in userMap.entries) {
            userList.add(
                Action(
                    buttonText = useraction.title,
                    iconRes = R.drawable.ic_user_options,
                    activityClass = BackgroundActionActivity::class.java.name,
                    action = EventData.ActionUserActionPreCheck(useraction.id, useraction.title),
                    message = context.resources.getString(R.string.action_user_action_confirmation)
                )
            )
            aapsLogger.info(LTag.WEAR, """getSelectedActions: active ${useraction.title} guid=${useraction.id}""")
        }
        return userList
    }

    override fun getValidFor(): Long? {
        val userMap = getUserActionData(sp)
        if (userMap.entries.size == 0) return null

        val now = System.currentTimeMillis()
        val validFor = userMap.entries[0].timeStamp + 5 * 60 * 1000L - now

        //aapsLogger.info(LTag.WEAR, "getValidTill: sfm$sfm till$validTill d=$delta")
        return if (validFor > 0L) validFor else null
    }

    private fun getUserActionData(sp: SP): EventData.UserAction =
        EventData.deserialize(sp.getString(R.string.key_user_action_data, EventData.UserAction(arrayListOf()).serialize())) as EventData.UserAction

    override fun getResourceReferences(resources: Resources): List<Int> = listOf(R.drawable.ic_user_options)
}
