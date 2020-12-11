package info.nightscout.androidaps.queue.commands

import java.io.Serializable

/**
 * Implement this interface for every custom pump command that you want to be able to queue
 * See [info.nightscout.androidaps.interfaces.CommandQueueProvider.customCommand] for queuing a custom command.
 */
interface CustomCommand : Serializable {

    /**
     * @return short description of this command to be used in [info.nightscout.androidaps.queue.commands.Command.status]
     * The description is typically all caps.
     */
    val statusDescription: String
}