package app.aaps.core.interfaces.smsCommunicator

import android.telephony.SmsMessage

/**
 * Builds an [Sms] from Android's telephony type.
 *
 * This was a constructor on [Sms] itself, which put `android.telephony` in the class and kept the
 * whole SMS model - and therefore `SmsCommunicator`, and therefore automation's `ActionSendSMS` -
 * out of common code. Only the receiver that reads incoming PDUs needs it, and that is Android by
 * definition, so the Android part lives here instead.
 */
fun smsFromMessage(message: SmsMessage): Sms =
    Sms(
        phoneNumber = message.originatingAddress ?: "",
        text = message.messageBody
    ).also {
        it.date = message.timestampMillis
        it.received = true
        it.sent = false
    }
