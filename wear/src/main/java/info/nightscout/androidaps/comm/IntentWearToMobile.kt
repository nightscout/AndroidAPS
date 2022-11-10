package info.nightscout.androidaps.comm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.rx.weardata.EventData

class IntentWearToMobile(context: Context, command: String) : Intent(context, DataLayerListenerServiceWear::class.java) {
    init {
        action = DataLayerListenerServiceWear.INTENT_WEAR_TO_MOBILE
        addFlags(FLAG_ACTIVITY_NEW_TASK)
        putExtras(Bundle().also { bundle ->
            bundle.putString(DataLayerListenerServiceWear.KEY_ACTION_DATA, command)
        })
    }

    @Suppress("unused")
    constructor(context: Context, command: EventData) : this(context, command.serialize())
}