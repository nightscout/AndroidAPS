package app.aaps.plugins.sync.smsCommunicator

abstract class SmsAction(val pumpCommand: Boolean) {

    abstract suspend fun run()
}
