package app.aaps.pump.virtual

import app.aaps.core.utils.fabric.InstanceId

/** Unchanged from before the move: the Firebase installation id. */
internal actual fun virtualPumpSerialNumber(): String = InstanceId.instanceId
