@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.wearable.complications.ProviderUpdateRequester
import android.widget.Toast
import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.ECarbActivity
import info.nightscout.androidaps.interaction.actions.TreatmentActivity
import info.nightscout.androidaps.interaction.actions.WizardActivity
import info.nightscout.androidaps.interaction.menus.MainMenuActivity
import info.nightscout.androidaps.interaction.menus.StatusMenuActivity
import info.nightscout.androidaps.interaction.utils.Constants
import info.nightscout.androidaps.interaction.utils.DisplayFormat
import info.nightscout.androidaps.interaction.utils.WearUtil
import javax.inject.Inject

/*
 * Created by dlvoy on 2019-11-12
 */
class ComplicationTapBroadcastReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var wearUtil: WearUtil
    @Inject lateinit var displayFormat: DisplayFormat
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val extras = intent.extras
        val provider = extras!!.getParcelable<ComponentName>(EXTRA_PROVIDER_COMPONENT)
        val complicationId = extras.getInt(EXTRA_COMPLICATION_ID)
        val complicationAction = extras.getString(EXTRA_COMPLICATION_ACTION, ComplicationAction.MENU.toString())
        var action = ComplicationAction.MENU
        try {
            action = ComplicationAction.valueOf(complicationAction)
        } catch (ex: IllegalArgumentException) {
            // but how?
            aapsLogger.error(LTag.WEAR, "Cannot interpret complication action: $complicationAction")
        } catch (ex: NullPointerException) {
            aapsLogger.error(LTag.WEAR, "Cannot interpret complication action: $complicationAction")
        }
        action = remapActionWithUserPreferences(action)

        // Request an update for the complication that has just been tapped.
        val requester = ProviderUpdateRequester(context, provider)
        requester.requestUpdate(complicationId)
        var intentOpen: Intent? = null
        when (action) {
            ComplicationAction.NONE                                         ->                 // do nothing
                return

            ComplicationAction.WIZARD                                       -> intentOpen = Intent(context, WizardActivity::class.java)
            ComplicationAction.BOLUS                                        -> intentOpen = Intent(context, TreatmentActivity::class.java)
            ComplicationAction.E_CARB                                       -> intentOpen = Intent(context, ECarbActivity::class.java)
            ComplicationAction.STATUS                                       -> intentOpen = Intent(context, StatusMenuActivity::class.java)

            ComplicationAction.WARNING_OLD, ComplicationAction.WARNING_SYNC -> {
                val oneAndHalfMinuteAgo = wearUtil.timestamp() - (Constants.MINUTE_IN_MS + Constants.SECOND_IN_MS * 30)
                val since = extras.getLong(EXTRA_COMPLICATION_SINCE, oneAndHalfMinuteAgo)
                @StringRes val labelId = if (action == ComplicationAction.WARNING_SYNC) R.string.msg_warning_sync else R.string.msg_warning_old
                val msg = String.format(context.getString(labelId), displayFormat.shortTimeSince(since))
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }

            ComplicationAction.MENU                                         -> intentOpen = Intent(context, MainMenuActivity::class.java)
        }
        if (intentOpen != null) {
            // Perform intent - open dialog
            intentOpen.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intentOpen)
        }
    }

    private val complicationTapAction: String
        get() = sp.getString(R.string.key_complication_tap_action, "default")

    private fun remapActionWithUserPreferences(originalAction: ComplicationAction): ComplicationAction {
        val userPrefAction = complicationTapAction
        return when (originalAction) {
            ComplicationAction.WARNING_OLD, ComplicationAction.WARNING_SYNC ->                 // warnings cannot be reconfigured by user
                originalAction

            else                                                            -> when (userPrefAction) {
                "menu"    -> ComplicationAction.MENU
                "wizard"  -> ComplicationAction.WIZARD
                "bolus"   -> ComplicationAction.BOLUS
                "ecarb"   -> ComplicationAction.E_CARB
                "status"  -> ComplicationAction.STATUS
                "none"    -> ComplicationAction.NONE
                "default" -> originalAction
                else      -> originalAction
            }
        }
    }

    companion object {

        private const val EXTRA_PROVIDER_COMPONENT = "info.nightscout.androidaps.complications.action.PROVIDER_COMPONENT"
        private const val EXTRA_COMPLICATION_ID = "info.nightscout.androidaps.complications.action.COMPLICATION_ID"
        private const val EXTRA_COMPLICATION_ACTION = "info.nightscout.androidaps.complications.action.COMPLICATION_ACTION"
        private const val EXTRA_COMPLICATION_SINCE = "info.nightscout.androidaps.complications.action.COMPLICATION_SINCE"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getTapActionIntent(
            context: Context, provider: ComponentName?, complicationId: Int, action: ComplicationAction
        ): PendingIntent {
            val intent = Intent(context, ComplicationTapBroadcastReceiver::class.java)
            intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider)
            intent.putExtra(EXTRA_COMPLICATION_ID, complicationId)
            intent.putExtra(EXTRA_COMPLICATION_ACTION, action.toString())

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                context, complicationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getTapWarningSinceIntent(
            context: Context?, provider: ComponentName?, complicationId: Int, action: ComplicationAction, since: Long
        ): PendingIntent {
            val intent = Intent(context, ComplicationTapBroadcastReceiver::class.java)
            intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider)
            intent.putExtra(EXTRA_COMPLICATION_ID, complicationId)
            intent.putExtra(EXTRA_COMPLICATION_ACTION, action.toString())
            intent.putExtra(EXTRA_COMPLICATION_SINCE, since)

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                context, complicationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}