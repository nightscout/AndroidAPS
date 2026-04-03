package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.ui.compose.icons.IcPluginNsClientBg
import app.aaps.plugins.source.compose.BgSourceComposeContent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientSourcePlugin @Inject constructor(
    rh: ResourceHelper,
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
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_nsclient_bg)
        .icon(IcPluginNsClientBg)
        .pluginName(R.string.ns_client_bg)
        .shortName(R.string.ns_client_bg_short)
        .description(R.string.description_source_ns_client)
        .alwaysEnabled(config.AAPSCLIENT)
        .setDefault(config.AAPSCLIENT),
    aapsLogger, rh
), BgSource, NSClientSource
