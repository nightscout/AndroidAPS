package app.aaps.ios.shell.missing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * A `Safety` plugin so the plugin list has one, and **nothing else**.
 *
 * `PluginStore.activeSafety` does `getSpecificPluginsListByInterface(Safety::class).first()`, so an
 * empty list is not a missing feature there - it throws, and the preferences screen cannot open.
 * That was the last crash before the app came up.
 *
 * `Safety` itself is an empty marker interface, so satisfying it is free. What is **not** here is
 * the part that matters: the real `SafetyPlugin` also implements `PluginConstraints`, and that is
 * where the limits live - maximum bolus, maximum basal, whether closed loop is allowed, whether the
 * pump can do temporary basals. This class implements none of it, so **no safety constraint is
 * applied on iOS**.
 *
 * That is survivable only because of what an iOS build currently is: a follower client that reaches
 * no pump and delivers no insulin, so there is no command for a constraint to limit. It stops being
 * survivable the moment that changes.
 *
 * The real plugin is `plugins/constraints/androidMain/.../safety/SafetyPlugin.kt`. Nothing in it is
 * Android - it is arithmetic and preference reads over `ConstraintsChecker`, `HardLimits` and
 * `ActivePlugin` - so it should be ported, and this class deleted, rather than filled in here.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(800)
@SingleIn(AppScope::class)
class IosSafetyPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: TextResolver
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(TextRef.Literal("Safety"))
        .icon(Icons.Default.Shield),
    aapsLogger, rh
), Safety
