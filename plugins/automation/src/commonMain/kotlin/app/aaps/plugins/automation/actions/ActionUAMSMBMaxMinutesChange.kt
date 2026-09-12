package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.AutomationStrings

/** Changes the "UAM max minutes of basal to limit SMB" preference. */
class ActionUAMSMBMaxMinutesChange(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    preferences: Preferences
) : ActionSMBMaxMinutesChangeBase(
    aapsLogger, rh, pumpEnactResultProvider, preferences,
    IntKey.ApsUamMaxMinutesOfBasalToLimitSmb,
    AutomationStrings.changeUamSmbMaxMinutes,
    AutomationStrings.changeUamSmbMaxMinutesTo
)
