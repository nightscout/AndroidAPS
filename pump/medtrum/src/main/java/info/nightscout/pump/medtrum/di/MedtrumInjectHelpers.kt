package info.nightscout.pump.medtrum.di

import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
annotation class MedtrumPluginQualifier

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope
