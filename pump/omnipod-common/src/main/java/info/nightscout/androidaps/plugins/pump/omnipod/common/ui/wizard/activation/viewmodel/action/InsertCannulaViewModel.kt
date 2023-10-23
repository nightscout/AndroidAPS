package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import dagger.android.HasAndroidInjector

abstract class InsertCannulaViewModel(
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : PodActivationActionViewModelBase(injector, logger, aapsSchedulers)