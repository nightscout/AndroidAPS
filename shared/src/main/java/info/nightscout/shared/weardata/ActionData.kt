package info.nightscout.shared.weardata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class ActionData {

    fun serialize() = Json.encodeToString(serializer(), this)

    companion object {

        fun deserialize(json: String) = Json.decodeFromString(serializer(), json)
    }
    // Wear -> Mobile
    @Serializable
    data class Pong(val timeStamp: Long) : ActionData()
    @Serializable
    data class Bolus(val insulin: Double, val carbs: Int) : ActionData()
    @Serializable
    data class ProfileSwitch(val timeShift: Int, val percentage: Int) : ActionData()

    @Serializable
    data class OpenProfileSwitch(val timeShift: Int, val percentage: Int) : ActionData()

    // Mobile -> Wear
    @Serializable
    data class Ping(val timeStamp: Long) : ActionData()
    @Serializable
    data class ConfirmAction(val title: String, val message: String, val originalCommand: ActionData) : ActionData()
    @Serializable
    data class ChangeAction(val title: String, val message: String, val originalCommand: ActionData) : ActionData()
}