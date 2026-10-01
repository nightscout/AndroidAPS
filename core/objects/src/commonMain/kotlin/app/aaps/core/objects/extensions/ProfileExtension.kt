package app.aaps.core.objects.extensions

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.Profile
import kotlin.math.abs

/**
 * The BG target the APS is currently running (in mg/dL) when it differs from the plain profile target,
 * i.e. the loop (or, on a client, the master) has overridden it — otherwise `null`.
 *
 * The profile side uses [Profile.getRoundedTargetMgdl] so it's compared the same way the APS rounds
 * min_bg/max_bg to 0.1 mg/dL; without that a non-integer mmol→mg/dL conversion would make every loop
 * run look "adjusted".
 */
fun Profile.apsAdjustedTargetMgdl(loop: Loop, config: Config, deviceStatus: ProcessedDeviceStatusData): Double? {
    val targetUsed = when {
        config.APS        -> loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0
        config.AAPSCLIENT -> deviceStatus.getAPSResult()?.targetBG ?: 0.0
        else              -> 0.0
    }
    return if (targetUsed != 0.0 && abs(getRoundedTargetMgdl() - targetUsed) > 0.01) targetUsed else null
}
