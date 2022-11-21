package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger

abstract class PodActivationActionViewModelBase(
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : ActionViewModelBase(injector, logger, aapsSchedulers) {

    abstract fun isPodInAlarm(): Boolean

    abstract fun isPodActivationTimeExceeded(): Boolean

    abstract fun isPodDeactivatable(): Boolean
}