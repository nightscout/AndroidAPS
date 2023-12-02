package info.nightscout.pump.combo.di

import dagger.Module

@Module(includes = [
    ComboActivitiesModule::class
])
open class ComboModule