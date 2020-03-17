package info.nightscout.androidaps.plugins.constraints.phoneChecker

import android.os.Build
import com.scottyab.rootbeer.RootBeer
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ConstraintsInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType

object PhoneCheckerPlugin : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.phonechecker)
), ConstraintsInterface {

    var phoneRooted: Boolean = false
    var devMode: Boolean = false
    val phoneModel: String = Build.MODEL
    val manufacturer: String = Build.MANUFACTURER

    private fun isDevModeEnabled(): Boolean {
        return android.provider.Settings.Secure.getInt(MainApp.instance().contentResolver,
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    override fun onStart() {
        super.onStart()
        phoneRooted = RootBeer(MainApp.instance()).isRootedWithoutBusyBoxCheck()
        devMode = isDevModeEnabled()
    }
}