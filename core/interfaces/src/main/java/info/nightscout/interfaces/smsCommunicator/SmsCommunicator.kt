package info.nightscout.interfaces.smsCommunicator

interface SmsCommunicator {

    var messages: ArrayList<Sms>
    fun sendNotificationToAllNumbers(text: String): Boolean
    fun sendSMS(sms: Sms): Boolean
    fun isEnabled(): Boolean
}