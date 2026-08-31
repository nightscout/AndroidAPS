package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.interfaces.TextRef
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
class NSClientSourcePlugin @Inject constructor(
    override val rh: TextResolver,
    aapsLogger: AAPSLogger,
    config: Config,
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
        .shortName(SourceStrings.ns_client_bg_short)
        .description(SourceStrings.description_source_ns_client)
        .alwaysEnabled(config.AAPSCLIENT)
        .setDefault(config.AAPSCLIENT),
    aapsLogger, rh
), BgSource, NSClientSource
