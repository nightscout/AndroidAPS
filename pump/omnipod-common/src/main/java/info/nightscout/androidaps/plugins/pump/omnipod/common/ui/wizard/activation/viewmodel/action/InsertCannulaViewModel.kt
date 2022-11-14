package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import dagger.android.HasAndroidInjector
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger

abstract class InsertCannulaViewModel(
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : PodActivationActionViewModelBase(injector, logger, aapsSchedulers)