package info.nightscout.shared.weardata

import android.content.Context
import info.nightscout.shared.R
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PropertyName")
@Singleton
class WearConstants @Inject constructor(private val context: Context) {

    // Paths must be defined in manifest
    // mobile -> wear (data)
    val M_W_DATA get() = context.getString(R.string.path_watch_data)
    val M_W_STATUS get() = context.getString(R.string.path_status)
    val M_W_PREFERENCES get() = context.getString(R.string.path_preferences)
    val M_W_QUICK_WIZARD get() = context.getString(R.string.path_quick_wizard)
    val M_W_BASAL get() = context.getString(R.string.path_basal)
    val M_W_BOLUS_PROGRESS get() = context.getString(R.string.path_bolus_progress)
    val M_W_ACTION_CONFIRMATION_REQUEST get() = context.getString(R.string.path_action_confirmation)
    val M_W_ACTION_CHANGE_CONFIRMATION_REQUEST get() = context.getString(R.string.path_change_confirmation_request)
    val M_W_ACTION_CANCEL_NOTIFICATION_REQUEST get() = context.getString(R.string.path_cancel_notification_request)

    // mobile -> wear (message)
    val M_W_OPEN_SETTINGS get() = context.getString(R.string.path_open_wear_setting)
    val M_W_PING get() = context.getString(R.string.path_ping)

    // wear -> mobile (message)
    val W_M_RESEND_DATA get() = context.getString(R.string.path_resend_data_request)
    val W_M_CANCEL_BOLUS get() = context.getString(R.string.path_cancel_bolus_on_phone)
    val W_M_CONFIRM_ACTION get() = context.getString(R.string.path_confirm_action)
    val W_M_INITIATE_ACTION get() = context.getString(R.string.path_initiate_action_on_phone)
    val W_M_PONG get() = context.getString(R.string.path_pong)

    companion object {

        // actions for WEAR_INITIATE_ACTION_ON_PHONE
        // used by
        //      DataLayerListenerService::initiateAction
        //      ActionStringHandler::handleInitiateActionOnPhone
        //      EventWearInitiateAction
        const val ACTION_FILL_PRESET = "fillpreset"
        const val ACTION_FILL = "fill"
        const val ACTION_BOLUS = "bolus"
        const val ACTION_TEMPORARY_TARGET = "temptarget"
        const val ACTION_STATUS = "status"
        const val ACTION_WIZARD = "wizard"
        const val ACTION_WIZARD2 = "wizard2"
        const val ACTION_QUICK_WIZARD = "quick_wizard"
        const val ACTION_OPEN_CPP = "opencpp"
        const val ACTION_CPP_SET = "cppset"
        const val ACTION_TDD_STATS = "tddstats"
        const val ACTION_E_CARBS = "ecarbs"
        const val ACTION_CHANGE_REQUEST = "changeRequest"
        const val ACTION_CANCEL_CHANGE_REQUEST = "cancelChangeRequest"
        const val ACTION_DISMISS_OVERVIEW_NOTIF = "dismissoverviewnotification"

        //data keys
        const val KEY_ACTION_DATA = "actionData"
    }
}
