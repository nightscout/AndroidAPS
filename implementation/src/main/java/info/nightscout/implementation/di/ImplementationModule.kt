package info.nightscout.implementation.di

import dagger.Module

@Module(
    includes = [
        CommandQueueModule::class
    ]
)

@Suppress("unused")
abstract class ImplementationModule