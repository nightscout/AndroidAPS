package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.Sms

interface SmsCommunicator {

    var messages: ArrayList<Sms>
    fun sendNotificationToAllNumbers(text: String): Boolean
    fun sendSMS(sms: Sms): Boolean
}