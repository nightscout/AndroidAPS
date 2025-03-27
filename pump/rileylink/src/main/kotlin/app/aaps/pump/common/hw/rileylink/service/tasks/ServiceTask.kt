package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import dagger.android.HasAndroidInjector
import javax.inject.Inject

@Suppress("LeakingThis")
open class ServiceTask(val injector: HasAndroidInjector) : Runnable {

    @Inject lateinit var activePlugin: ActivePlugin

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