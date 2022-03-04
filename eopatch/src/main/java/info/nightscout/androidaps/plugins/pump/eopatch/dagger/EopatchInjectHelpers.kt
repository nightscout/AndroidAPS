package info.nightscout.androidaps.plugins.pump.eopatch.dagger

import javax.inject.Qualifier
import javax.inject.Scope

@Qualifier
annotation class EopatchPluginQualifier

@MustBeDocumented
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope
