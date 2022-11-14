package info.nightscout.plugins.aps.di

import dagger.Module
import info.nightscout.plugins.di.AutotuneModule

@Module(
    includes = [
        AutotuneModule::class,
        AlgModule::class
    ]
)

@Suppress("unused")
abstract class ApsModule