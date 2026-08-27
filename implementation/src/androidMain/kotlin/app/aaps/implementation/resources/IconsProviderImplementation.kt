package app.aaps.implementation.resources

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.implementation.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

// Metro builds this now; Dagger gets it through a @Provides delegate in `:app`. Scoped with Metro's
// @SingleIn, not javax @Singleton - the graph is generated in `:app`, which has no Dagger interop, so
// a javax scope there is ignored and every read would build a new one.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IconsProviderImplementation @Inject constructor(private val config: Config) : IconsProvider {

    override fun getIcon(): Int =
        when {
            config.AAPSCLIENT3 -> app.aaps.core.ui.R.mipmap.ic_greenowl
            config.AAPSCLIENT2 -> app.aaps.core.ui.R.mipmap.ic_blueowl
            config.AAPSCLIENT1 -> app.aaps.core.ui.R.mipmap.ic_yellowowl
            config.PUMPCONTROL -> app.aaps.core.ui.R.mipmap.ic_pumpcontrol
            else               -> app.aaps.core.ui.R.mipmap.ic_launcher
        }

    override fun getNotificationIcon(): Int =
        when {
            config.AAPSCLIENT  -> R.drawable.ic_notif_nsclient
            config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            else               -> app.aaps.core.ui.R.drawable.ic_notif_aaps
        }
}