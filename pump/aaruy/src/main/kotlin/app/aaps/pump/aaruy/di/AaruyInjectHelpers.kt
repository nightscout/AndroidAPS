package app.aaps.pump.aaruy.di

import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
annotation class AaruyPluginQualifier

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope
