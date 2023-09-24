package info.nightscout.androidaps.events

import android.content.Context
import app.aaps.core.interfaces.rx.events.Event

@Suppress("unused")
class EventWearPreferenceChange : Event {

    var changedKey: String? = null
        private set

    constructor(key: String) {
        changedKey = key
    }

    constructor(context: Context, resourceID: Int) {
        changedKey = context.getString(resourceID)
    }

    fun isChanged(context: Context, id: Int): Boolean {
        return changedKey == context.getString(id)
    }
}