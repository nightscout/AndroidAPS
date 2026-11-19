package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.ui.compose.icons.IcPluginNsClientBg
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@ContributesBinding(AppScope::class, binding = binding<NSClientSource>())
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(410)
@SingleIn(AppScope::class)
@Inject
class NSClientSourcePlugin(
    override val rh: TextResolver,
    aapsLogger: AAPSLogger,
    config: Config,
    notificationManager: NotificationManager
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(SourceStrings.ns_client_bg)
            )
        }
        .icon(IcPluginNsClientBg)
        .pluginName(SourceStrings.ns_client_bg)
        .description(SourceStrings.description_source_ns_client)
        // Forced on for a client, which has no other BG source. On a master it stays the user's choice, so
        // this is one-sided on purpose - no forced-off half.
        .enforce(EnforcedState.Enabled) { config.AAPSCLIENT }
        // TEMPORARY: also the default for the full app on iOS. Nightscout is the only glucose source iOS
        // has today. On Android the full app's default source is `DexcomPlugin`, which exists only on
        // Android, so without this the iOS full app has no default source at all and its start stops with
        // "Default plugin not found". Drop the iOS half once iOS has a glucose source of its own -
        // `_docs/ios_todo.md` tracks it. Matched on iOS exactly, not on "not Android", so this cannot
        // quietly reach another platform.
        .setDefault(config.AAPSCLIENT || config.platform == AppPlatform.Ios),
    aapsLogger, rh, notificationManager
), BgSource, NSClientSource
