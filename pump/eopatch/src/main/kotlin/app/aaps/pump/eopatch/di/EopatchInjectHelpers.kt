package app.aaps.pump.eopatch.di

import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
annotation class EopatchPluginQualifier

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope
