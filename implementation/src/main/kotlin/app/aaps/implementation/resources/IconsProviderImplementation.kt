package app.aaps.implementation.resources

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.implementation.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconsProviderImplementation @Inject constructor(private val config: Config) : IconsProvider {

    override fun getIcon(): Int =
        when {
            config.NSCLIENT2   -> app.aaps.core.ui.R.mipmap.ic_blueowl
            config.NSCLIENT1   -> app.aaps.core.ui.R.mipmap.ic_yellowowl
            config.PUMPCONTROL -> app.aaps.core.ui.R.mipmap.ic_pumpcontrol
            else               -> app.aaps.core.ui.R.mipmap.ic_launcher
        }

    override fun getNotificationIcon(): Int =
        when {
            config.NSCLIENT    -> R.drawable.ic_notif_nsclient
            config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            else               -> app.aaps.core.objects.R.drawable.ic_notif_aaps
        }
}