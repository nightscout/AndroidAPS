package info.nightscout.androidaps.plugins.profile.ns

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.events.EventProfileStoreChanged
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ProfileInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.profile.ns.events.EventNSProfileUpdateGUI
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSProfilePlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.PROFILE)
    .fragmentClass(NSProfileFragment::class.java.name)
    .pluginIcon(R.drawable.ic_nightscout_profile)
    .pluginName(R.string.nsprofile)
    .shortName(R.string.profileviewer_shortname)
    .alwaysEnabled(config.NSCLIENT)
    .alwaysVisible(config.NSCLIENT)
    .showInList(!config.NSCLIENT)
    .description(R.string.description_profile_nightscout),
    aapsLogger, resourceHelper, injector
), ProfileInterface {

    private var profile: ProfileStore? = null

    override fun onStart() {
        super.onStart()
        loadNSProfile()
    }

    fun handleNewData(intent: Intent) {
        val bundles = intent.extras ?: return

        @Suppress("SpellCheckingInspection")
        val activeProfile = bundles.getString("activeprofile")
        val profileString = bundles.getString("profile")
        profile = ProfileStore(injector, JSONObject(profileString))
        storeNSProfile()
        if (isEnabled()) {
            rxBus.send(EventProfileStoreChanged())
            rxBus.send(EventNSProfileUpdateGUI())
        }
        aapsLogger.debug(LTag.PROFILE, "Received profileStore: $activeProfile $profile")
    }

    private fun storeNSProfile() {
        sp.putString("profile", profile!!.data.toString())
        aapsLogger.debug(LTag.PROFILE, "Storing profile")
    }

    private fun loadNSProfile() {
        aapsLogger.debug(LTag.PROFILE, "Loading stored profile")
        val profileString = sp.getStringOrNull("profile", null)
        if (profileString != null) {
            aapsLogger.debug(LTag.PROFILE, "Loaded profile: $profileString")
            profile = ProfileStore(injector, JSONObject(profileString))
        } else {
            aapsLogger.debug(LTag.PROFILE, "Stored profile not found")
            // force restart of nsclient to fetch profile
            rxBus.send(EventNSClientRestart())
        }
    }

    override fun getProfile(): ProfileStore? {
        return profile
    }

    override fun getProfileName(): String {
        return profile!!.getDefaultProfileName()!!
    }

}