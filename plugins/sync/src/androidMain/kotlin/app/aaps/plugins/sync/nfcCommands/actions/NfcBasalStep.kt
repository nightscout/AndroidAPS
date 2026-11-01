package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.plugins.sync.nfcCommands.NfcDefaults

/**
 * Pump basal step helpers.
 *
 * These were methods on `NfcCommandsPlugin`, which is why the two temporary basal actions needed the
 * whole plugin. [roundUpToStep] needs nothing at all, and [pumpBasalDurationStep] needs only the
 * active pump, which those actions already receive.
 */

/** The active pump's temporary basal duration step, in minutes. 60 when the pump does not say. */
internal fun pumpBasalDurationStep(activePlugin: ActivePlugin): Int =
    activePlugin.activePump.model().tbrSettings()?.durationStep
        ?: NfcDefaults.PUMP_BASAL_DURATION_STEP_MINUTES

/**
 * Rounds a duration up to the next whole multiple of [step].
 *
 * A tag is written for whichever pump was active at the time, so a tag written for a pump with a 30
 * minute step has to still work on one with a 15 or 60 minute step.
 */
internal fun roundUpToStep(value: Int, step: Int): Int =
    if (value % step == 0) value else ((value / step) + 1) * step
