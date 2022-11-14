package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import info.nightscout.interfaces.plugin.ActivePlugin
import javax.inject.Inject

@Suppress("LeakingThis")
open class ServiceTask constructor(val injector: HasAndroidInjector) : Runnable {

    @Inject lateinit var activePlugin: ActivePlugin

    var completed = false

    init {
        injector.androidInjector().inject(this)
    }

    override fun run() {}

    // This function is called by UI thread before running async thread.
    fun preOp() {}

    // This function is called by UI thread after running async thread.
    fun postOp() {}

    val isRileyLinkDevice: Boolean
        get() = activePlugin.activePump is RileyLinkPumpDevice

    val pumpDevice: RileyLinkPumpDevice?
        get() = if (isRileyLinkDevice) activePlugin.activePump as RileyLinkPumpDevice else null

}