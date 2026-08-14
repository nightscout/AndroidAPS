package app.aaps.plugins.automation.actions

import app.aaps.core.keys.IntKey
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector

/** Changes the "UAM max minutes of basal to limit SMB" preference. */
class ActionUAMSMBMaxMinutesChange(injector: HasAndroidInjector) : ActionSMBMaxMinutesChangeBase(
    injector,
    IntKey.ApsUamMaxMinutesOfBasalToLimitSmb,
    R.string.changeUamSmbMaxMinutes,
    R.string.changeUamSmbMaxMinutesTo
)
