package info.nightscout.androidaps.interfaces

interface SmsCommunicator {

    fun sendNotificationToAllNumbers(text: String): Boolean
}