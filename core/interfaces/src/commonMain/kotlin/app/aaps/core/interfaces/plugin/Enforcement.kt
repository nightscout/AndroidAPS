package app.aaps.core.interfaces.plugin

import app.aaps.core.keys.interfaces.TextRef

/**
 * A state this build forces a plugin into, whatever the user stored.
 *
 * There is deliberately no third "user decides" value: that is the ABSENCE of an enforcement. A plugin
 * with no enforcement that applies keeps whatever the user chose, which is the common case and therefore
 * the one nobody has to write down.
 */
enum class EnforcedState { Enabled, Disabled }

/**
 * One rule of the form "in this kind of build, this plugin's enabled state is not the user's to pick".
 *
 * ## Why this exists rather than a pair of flags
 *
 * The enabled state used to be decided by two mechanisms that were a pair without saying so:
 * `PluginDescription.alwaysEnabled` forced ON from the builder, and an overridable
 * `PluginBase.specialEnableCondition()` forced OFF from the class body, ANDed with the stored state a
 * hundred lines away. Nothing connected them, and they interacted through short-circuit order - which is
 * how `LoopPlugin` ended up with a temp basal check that could never run, because `alwaysEnabled(config.APS)`
 * returned first on every build that had a real pump. Six months, unnoticed.
 *
 * ## The rules
 *
 * - **Absent means the user decides.** Most plugins declare nothing.
 * - **Disabled wins.** If two enforcements apply and disagree, the plugin is off. Failing closed is the
 *   right default here, and `PluginEnforcementTest` fails the build on a plugin that can hold both at once.
 * - **[applies] should read what is constant for the process** - the build flavour, engineering mode, an
 *   external option file. `isEnabled()` is answered on a hot path, so it must stay cheap and must not
 *   throw. `RandomBgPlugin` is the one exception today: it also reads `virtualPump.isEnabled()`, so its
 *   answer changes when the user picks another pump. That is tolerable because it cannot throw, but it
 *   means an enforcement must never read a plugin that reads it back - there is no cycle detection here.
 * - **A rule that varies with the live pump is a *constraint*, not an enforcement.** See
 *   `SafetyPlugin.isLoopInvocationAllowed`, which refuses the loop on a pump that cannot do temp basals:
 *   re-evaluated every run, and it carries a reason the user can read. Three APS plugins used to duplicate
 *   that rule as a plugin condition; the copies are gone.
 *
 * @param reason why the state is forced. Not shown anywhere yet; it is here so the Config Builder can
 *   later say *"Not available on a client"* beside a locked switch instead of offering a dead one.
 */
class Enforcement(
    val state: EnforcedState,
    val reason: TextRef? = null,
    val applies: () -> Boolean = { true }
)
