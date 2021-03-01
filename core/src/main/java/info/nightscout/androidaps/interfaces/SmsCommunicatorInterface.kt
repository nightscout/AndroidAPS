package info.nightscout.androidaps.interfaces

interface SmsCommunicatorInterface {

    fun sendNotificationToAllNumbers(text: String): Boolean
}