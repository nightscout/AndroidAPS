package app.aaps.wear.comm

import android.content.Context
import android.content.Intent

class IntentCancelNotification(context: Context) : Intent(context, DataLayerListenerServiceWear::class.java) {
    init {
        action = DataLayerListenerServiceWear.INTENT_CANCEL_NOTIFICATION
    }
}