package app.aaps.pump.omnipod.eros.di

import androidx.lifecycle.ViewModel
import app.aaps.pump.omnipod.common.di.OmnipodPluginQualifier
import app.aaps.pump.omnipod.common.di.ViewModelKey
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.info.AttachPodViewModel
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.info.PodActivatedViewModel
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.info.StartPodActivationViewModel
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDeactivatedViewModel
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDiscardedViewModel
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.StartPodDeactivationViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.action.ErosInitializePodViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.action.ErosInsertCannulaViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.info.ErosAttachPodViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.info.ErosPodActivatedViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.info.ErosStartPodActivationViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.action.ErosDeactivatePodViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info.ErosPodDeactivatedViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info.ErosPodDiscardedViewModel
import app.aaps.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.info.ErosStartPodDeactivationViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

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