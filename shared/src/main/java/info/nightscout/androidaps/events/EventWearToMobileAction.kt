package info.nightscout.androidaps.events

import info.nightscout.shared.weardata.ActionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class EventWearToMobileAction(val actionData: ActionData) : Event()