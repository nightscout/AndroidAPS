package info.nightscout.androidaps.events

import info.nightscout.shared.weardata.ActionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class EventWearToMobileConfirm(val actionData: ActionData) : Event()