package info.nightscout.implementation.resources

import info.nightscout.implementation.R
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.ui.IconsProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconsProviderImplementation @Inject constructor(private val config: Config) : IconsProvider {

    override fun getIcon(): Int =
        when {
            config.NSCLIENT2    -> info.nightscout.core.ui.R.mipmap.ic_blueowl
            config.NSCLIENT1    -> info.nightscout.core.ui.R.mipmap.ic_yellowowl
            config.PUMPCONTROL -> info.nightscout.core.ui.R.mipmap.ic_pumpcontrol
            else               -> info.nightscout.core.ui.R.mipmap.ic_launcher
        }

    override fun getNotificationIcon(): Int =
        when {
            config.NSCLIENT    -> R.drawable.ic_notif_nsclient
            config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            else               -> info.nightscout.core.main.R.drawable.ic_notif_aaps
        }
}