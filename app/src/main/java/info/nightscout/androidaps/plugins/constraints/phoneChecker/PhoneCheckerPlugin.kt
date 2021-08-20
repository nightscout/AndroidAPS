package info.nightscout.androidaps.plugins.constraints.phoneChecker

import android.content.Context
import android.os.Build
import com.scottyab.rootbeer.RootBeer
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ConstraintsInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneCheckerPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val context: Context
) : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.phonechecker),
    aapsLogger, resourceHelper, injector
), ConstraintsInterface {

    var phoneRooted: Boolean = false
    var devMode: Boolean = false
    val phoneModel: String = Build.MODEL
    val manufacturer: String = Build.MANUFACTURER

    private fun isDevModeEnabled(): Boolean {
        return android.provider.Settings.Secure.getInt(context.contentResolver,
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }

    override fun onStart() {
        super.onStart()
        phoneRooted = RootBeer(context).isRooted
        devMode = isDevModeEnabled()
    }
}