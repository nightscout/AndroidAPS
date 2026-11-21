package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.latest

/**
 * The profile a new patch takes its basal from: the requested one, the profile switch that the wizard's profile step
 * or the user made, else the one the patch model holds. The Equil wizard takes the same,
 * `PumpSync.expectedPumpState().profile`.
 *
 * The requested one comes first. While a patch is being activated the driver tells the queue that every profile is
 * already set, so `setNewBasalProfile` does not run and the patch model can hold an older profile: one from before a
 * profile switch made during the activation, or while no patch was paired (the queue drops every command then).
 *
 * The one the patch model holds is only the fallback: on a phone where no profile ever ran there is no requested one,
 * and the wizard would stop at the basal with "profile not set". The profile is kept on the patch model, as
 * `setNewBasalProfile` would have done.
 */
internal suspend fun CarelevoPatch.activationProfile(pumpSync: PumpSync): Profile? =
    pumpSync.expectedPumpState().profile?.also { setProfile(it) } ?: profile.latest
