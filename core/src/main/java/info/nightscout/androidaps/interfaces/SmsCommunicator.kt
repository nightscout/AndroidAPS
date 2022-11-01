package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.Sms

interface SmsCommunicator {

    fun sendNotificationToAllNumbers(text: String): Boolean
    fun sendSMS(sms: Sms): Boolean
}