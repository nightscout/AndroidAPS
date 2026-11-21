package app.aaps.core.interfaces.pump

/** None: the link closes as soon as the queue is empty, to save the background time of the wake. See the expect declaration. */
internal actual val defaultDisconnectWaitSeconds: Int = 0
