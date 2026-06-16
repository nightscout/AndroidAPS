package app.aaps.implementation.di

import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.implementation.bolus.WizardBolusExecutorImpl
import app.aaps.implementation.bolus.WizardExecutorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Bindings for the shared bolus execution spine (prepare → confirm → deliver), which lives in `:implementation`. */
@Module
@InstallIn(SingletonComponent::class)
interface BolusModule {

    @Binds fun bindWizardBolusExecutor(impl: WizardBolusExecutorImpl): WizardBolusExecutor

    /** Role-transparent recompute-bolus facade (QuickWizard WIZARD + manual wizard) — sibling of BatchExecutor. */
    @Binds fun bindWizardExecutor(impl: WizardExecutorImpl): WizardExecutor
}
