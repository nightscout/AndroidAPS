package app.aaps.wear.comm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.rx.weardata.EventData

class IntentWearToMobile(context: Context, command: String) : Intent(context, DataLayerListenerServiceWear::class.java) {
    init {
        action = DataLayerListenerServiceWear.INTENT_WEAR_TO_MOBILE
        putExtras(Bundle().also { bundle ->
            bundle.putString(DataLayerListenerServiceWear.KEY_ACTION_DATA, command)
        })
    }

    constructor(context: Context, command: EventData) : this(context, command.serialize())
}