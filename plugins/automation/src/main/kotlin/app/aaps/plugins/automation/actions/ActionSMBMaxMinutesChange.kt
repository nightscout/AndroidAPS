package app.aaps.plugins.automation.actions

import app.aaps.core.keys.IntKey
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector

/** Changes the "Max minutes of basal to limit SMB" preference. */
class ActionSMBMaxMinutesChange(injector: HasAndroidInjector) : ActionSMBMaxMinutesChangeBase(
    injector,
    IntKey.ApsMaxMinutesOfBasalToLimitSmb,
    R.string.changeSmbMaxMinutes,
    R.string.changeSmbMaxMinutesTo
)
