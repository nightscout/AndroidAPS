package app.aaps.pump.omnipod.dash.driver.event

import app.aaps.pump.omnipod.dash.driver.comm.Id
import app.aaps.pump.omnipod.dash.driver.pod.command.base.Command
import app.aaps.pump.omnipod.dash.driver.pod.response.Response

sealed class PodEvent {

    /* BT connection events */
    class AlreadyConnected(val bluetoothAddress: String) : PodEvent() {

        override fun toString(): String {
            return "AlreadyConnected(bluetoothAddress='$bluetoothAddress')"
        }
    }

    object AlreadyPaired : PodEvent()
    object Scanning : PodEvent()
    object BluetoothConnecting : PodEvent()
    class BluetoothConnected(val bluetoothAddress: String) : PodEvent() {

        override fun toString(): String {
            return "BluetoothConnected(bluetoothAddress='$bluetoothAddress')"
        }
    }

    object Pairing : PodEvent()
    class Paired(val uniqueId: Id) : PodEvent() {

        override fun toString(): String {
            return "Paired(uniqueId=$uniqueId)"
        }
    }

    object EstablishingSession : PodEvent()
    object Connected : PodEvent()

    /* Message exchange events */
    class CommandSending(val command: Command) : PodEvent() {

        override fun toString(): String {
            return "CommandSending(command=$command)"
        }
    }

    class CommandSent(val command: Command) : PodEvent() {

        override fun toString(): String {
            return "CommandSent(command=$command)"
        }
    }

    class CommandSendNotConfirmed(val command: Command) : PodEvent() {

        override fun toString(): String {
            return "CommandSentNotConfirmed(command=$command)"
        }
    }

    class ResponseReceived(
        val command: Command,
        val response: Response
    ) : PodEvent() {

        override fun toString(): String {
            return "ResponseReceived(command=$command, response=$response)"
        }
    }

    fun isCommandSent(): Boolean {
        return this is CommandSent || this is CommandSendNotConfirmed
    }
}
