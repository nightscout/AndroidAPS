package app.aaps.plugins.configuration.setupwizard.elements

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.scanForActivity
import javax.inject.Inject

class SWFragment @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    lateinit var fragmentName: String

    fun with(fragmentName: String): SWFragment {
        this.fragmentName = fragmentName
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val activity = layout.context.scanForActivity() ?: error("Activity not found")
        val fragment = activity.supportFragmentManager.fragmentFactory.instantiate(
            ClassLoader.getSystemClassLoader(),
            fragmentName
        )
        activity.supportFragmentManager.beginTransaction().add(layout.id, fragment, fragment.tag).commit()
    }
}