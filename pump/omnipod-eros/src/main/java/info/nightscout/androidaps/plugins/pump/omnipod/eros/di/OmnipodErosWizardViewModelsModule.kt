package info.nightscout.androidaps.plugins.pump.omnipod.eros.di

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.OmnipodPluginQualifier
import info.nightscout.androidaps.plugins.pump.omnipod.common.di.ViewModelKey
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.AttachPodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.PodActivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.info.StartPodActivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDeactivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDiscardedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.StartPodDeactivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.action.ErosInitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.action.ErosInsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.info.ErosAttachPodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.info.ErosPodActivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.info.ErosStartPodActivationViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.action.ErosDeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info.ErosPodDeactivatedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info.ErosPodDiscardedViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info.ErosStartPodDeactivationViewModel

@Module
@Suppress("unused")
abstract class OmnipodErosWizardViewModelsModule {
    // #### VIEW MODELS ############################################################################

    // POD ACTIVATION

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(StartPodActivationViewModel::class)
    internal abstract fun startPodActivationViewModel(viewModel: ErosStartPodActivationViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(InitializePodViewModel::class)
    internal abstract fun initializePodViewModel(viewModel: ErosInitializePodViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(AttachPodViewModel::class)
    internal abstract fun attachPodViewModel(viewModel: ErosAttachPodViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(InsertCannulaViewModel::class)
    internal abstract fun insertCannulaViewModel(viewModel: ErosInsertCannulaViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PodActivatedViewModel::class)
    internal abstract fun podActivatedViewModel(viewModel: ErosPodActivatedViewModel): ViewModel

    // POD DEACTIVATION
    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(StartPodDeactivationViewModel::class)
    internal abstract fun startPodDeactivationViewModel(viewModel: ErosStartPodDeactivationViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(DeactivatePodViewModel::class)
    internal abstract fun deactivatePodViewModel(viewModel: ErosDeactivatePodViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PodDeactivatedViewModel::class)
    internal abstract fun podDeactivatedViewModel(viewModel: ErosPodDeactivatedViewModel): ViewModel

    @Binds
    @IntoMap
    @OmnipodPluginQualifier
    @ViewModelKey(PodDiscardedViewModel::class)
    internal abstract fun podDiscardedViewModel(viewModel: ErosPodDiscardedViewModel): ViewModel
}