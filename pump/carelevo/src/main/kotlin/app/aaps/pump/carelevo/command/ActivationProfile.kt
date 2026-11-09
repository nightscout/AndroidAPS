package app.aaps.pump.carelevo.command

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.carelevo.common.CarelevoPatch
import kotlin.jvm.optionals.getOrNull

/**
 * The profile a new patch takes its basal from: the one the patch model holds, else the requested one, the profile
 * switch that the wizard's profile step or the user made. The Equil wizard takes the same,
 * `PumpSync.expectedPumpState().profile`.
 *
 * The requested one is needed for a first patch. Until a patch is paired the queue drops every command, so
 * `setNewBasalProfile` never stores a profile, and on a phone where no profile ever ran there is no running one to
 * take at the driver's start either. The wizard then stopped at the basal with "profile not set". The profile is kept
 * on the patch model, as `setNewBasalProfile` would have done.
 */
internal suspend fun CarelevoPatch.activationProfile(pumpSync: PumpSync): Profile? =
    profile.value?.getOrNull() ?: pumpSync.expectedPumpState().profile?.also { setProfile(it) }
