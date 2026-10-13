package app.aaps.wear.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.BundleCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.WizardActivity
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.interaction.menus.StatusMenuActivity
import app.aaps.wear.interaction.utils.Constants
import app.aaps.wear.interaction.utils.DisplayFormat
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Transparent activity to handle complication tap actions reliably.
 * Using an Activity instead of BroadcastReceiver ensures the tap works
 * even when the app is in Doze mode or deep sleep on Android 12+.
 *
 * This solves the ForegroundServiceStartNotAllowedException that occurs
 * when trying to start a foreground service from a BroadcastReceiver
 * while the app is in the background.
 */
class ComplicationTapActivity : DaggerAppCompatActivity() {

    @Inject lateinit var displayFormat: DisplayFormat
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger

    companion object {
        const val EXTRA_PROVIDER_COMPONENT = "info.nightscout.androidaps.complications.action.PROVIDER_COMPONENT"
        const val EXTRA_COMPLICATION_ID = "info.nightscout.androidaps.complications.action.COMPLICATION_ID"
        const val EXTRA_COMPLICATION_ACTION = "info.nightscout.androidaps.complications.action.COMPLICATION_ACTION"
        const val EXTRA_COMPLICATION_SINCE = "info.nightscout.androidaps.complications.action.COMPLICATION_SINCE"

        /**
         * Returns a pending intent for complication tap action.
         * Uses PendingIntent.getActivity() which can be triggered even when app is in background/doze.
         */
        fun getTapActionIntent(
            context: Context,
            provider: ComponentName?,
            complicationId: Int,
            action: ComplicationAction
        ): PendingIntent {
            val intent = Intent(context, ComplicationTapActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_PROVIDER_COMPONENT, provider)
                putExtra(EXTRA_COMPLICATION_ID, complicationId)
                putExtra(EXTRA_COMPLICATION_ACTION, action.toString())
            }

            // Use FLAG_IMMUTABLE for security (required on Android 12+)
            // Use UPDATE_CURRENT to ensure the intent is refreshed
            return PendingIntent.getActivity(
                context,
                complicationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**
         * Returns a pending intent for warning tap action with timestamp
         */
        fun getTapWarningSinceIntent(
            context: Context?,
            provider: ComponentName?,
            complicationId: Int,
            action: ComplicationAction,
            since: Long
        ): PendingIntent {
            val intent = Intent(context, ComplicationTapActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_PROVIDER_COMPONENT, provider)
                putExtra(EXTRA_COMPLICATION_ID, complicationId)
                putExtra(EXTRA_COMPLICATION_ACTION, action.toString())
                putExtra(EXTRA_COMPLICATION_SINCE, since)
            }

            return PendingIntent.getActivity(
                context,
                complicationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        aapsLogger.debug(LTag.WEAR, "ComplicationTapActivity started")

        try {
            handleComplicationTap()
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "Error handling complication tap", e)
        } finally {
            // Finish immediately - this is a transparent activity
            finish()
        }
    }

    private fun handleComplicationTap() {
        val extras = intent.extras ?: return

        val provider = BundleCompat.getParcelable(extras, EXTRA_PROVIDER_COMPONENT, ComponentName::class.java)

        val complicationId = extras.getInt(EXTRA_COMPLICATION_ID)
        val complicationActionStr = extras.getString(EXTRA_COMPLICATION_ACTION, ComplicationAction.MENU.toString())

        var action = ComplicationAction.MENU
        try {
            action = ComplicationAction.valueOf(complicationActionStr)
        } catch (_: IllegalArgumentException) {
            aapsLogger.error(LTag.WEAR, "Cannot interpret complication action: $complicationActionStr")
        } catch (_: NullPointerException) {
            aapsLogger.error(LTag.WEAR, "Cannot interpret complication action: $complicationActionStr")
        }

        action = remapActionWithUserPreferences(action)
        aapsLogger.debug(LTag.WEAR, "ComplicationTapActivity handling action: $action for complication: $complicationId")

        // Request an update for the complication that has just been tapped
        if (provider != null) {
            val requester = ComplicationDataSourceUpdateRequester.create(this, provider)
            requester.requestUpdate(complicationId)
        }

        var intentOpen: Intent? = null
        when (action) {
            ComplicationAction.NONE -> return

            ComplicationAction.WIZARD -> intentOpen = Intent(this, WizardActivity::class.java)
            ComplicationAction.BOLUS -> intentOpen = Intent(this, TreatmentActivity::class.java)
            ComplicationAction.E_CARB -> intentOpen = Intent(this, ECarbActivity::class.java)
            ComplicationAction.STATUS -> intentOpen = Intent(this, StatusMenuActivity::class.java)

            ComplicationAction.WARNING_OLD, ComplicationAction.WARNING_SYNC -> {
                val oneAndHalfMinuteAgo = System.currentTimeMillis() - (Constants.MINUTE_IN_MS + Constants.SECOND_IN_MS * 30)
                val since = extras.getLong(EXTRA_COMPLICATION_SINCE, oneAndHalfMinuteAgo)
                @StringRes val labelId = if (action == ComplicationAction.WARNING_SYNC) R.string.msg_warning_sync else R.string.msg_warning_old
                val msg = String.format(getString(labelId), displayFormat.shortTimeSince(since))
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }

            ComplicationAction.MENU -> intentOpen = Intent(this, MainMenuActivity::class.java)
        }

        if (intentOpen != null) {
            intentOpen.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intentOpen)
        }
    }

    private val complicationTapAction: String
        get() = sp.getString(R.string.key_complication_tap_action, "default")

    private fun remapActionWithUserPreferences(originalAction: ComplicationAction): ComplicationAction {
        val userPrefAction = complicationTapAction
        return when (originalAction) {
            ComplicationAction.WARNING_OLD, ComplicationAction.WARNING_SYNC -> originalAction

            else -> when (userPrefAction) {
                "menu" -> ComplicationAction.MENU
                "wizard" -> ComplicationAction.WIZARD
                "bolus" -> ComplicationAction.BOLUS
                "ecarb" -> ComplicationAction.E_CARB
                "status" -> ComplicationAction.STATUS
                "none" -> ComplicationAction.NONE
                "default" -> originalAction
                else -> originalAction
            }
        }
    }
}
