package info.nightscout.interfaces

import info.nightscout.interfaces.data.smsCommunicator.Sms

interface SmsCommunicator {

    var messages: ArrayList<Sms>
    fun sendNotificationToAllNumbers(text: String): Boolean
    fun sendSMS(sms: Sms): Boolean
}