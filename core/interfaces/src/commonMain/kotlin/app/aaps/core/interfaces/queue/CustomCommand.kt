package app.aaps.core.interfaces.queue

/**
 * Implement this interface for every custom pump command that you want to be able to queue
 * See [app.aaps.core.interfaces.queue.CommandQueue.customCommand] for queuing a custom command.
 */
interface CustomCommand {

    /**
     * @return short description of this command to be used in [app.aaps.core.interfaces.queue.Command.status]
     * The description is typically all caps.
     */
    val statusDescription: String
}