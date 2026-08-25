package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcPluginNsClientBg
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import javax.inject.Inject

// Registers itself into the plugin list. Scoped with Metro's own @SingleIn, not javax @Singleton - see
// the note on the other source plugins. It is also bound to an interface, and that binding is a
// @Provides delegate in `:app` rather than a Dagger @Binds, so Dagger hands out THIS instance.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(410)
@SingleIn(AppScope::class)
class NSClientSourcePlugin @Inject constructor(
    override val rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    config: Config,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.ns_client_bg)
            )
        }
        .icon(IcPluginNsClientBg)
        .pluginName(TextRef.AndroidRes(R.string.ns_client_bg))
        .shortName(TextRef.AndroidRes(R.string.ns_client_bg_short))
        .description(TextRef.AndroidRes(R.string.description_source_ns_client))
        .alwaysEnabled(config.AAPSCLIENT)
        .setDefault(config.AAPSCLIENT),
    aapsLogger, rh
), BgSource, NSClientSource
