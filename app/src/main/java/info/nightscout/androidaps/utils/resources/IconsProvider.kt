package info.nightscout.androidaps.utils.resources

import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconsProvider @Inject constructor() {

    fun getIcon(): Int =
        when {
            Config.NSCLIENT    -> R.mipmap.ic_yellowowl
            Config.PUMPCONTROL -> R.mipmap.ic_pumpcontrol
            else               -> R.mipmap.ic_launcher
        }

    fun getNotificationIcon(): Int =
        when {
            Config.NSCLIENT    -> R.drawable.ic_notif_nsclient
            Config.PUMPCONTROL -> R.drawable.ic_notif_pumpcontrol
            else               -> R.drawable.ic_notif_aaps
        }
}