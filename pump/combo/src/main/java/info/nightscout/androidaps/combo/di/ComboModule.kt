package info.nightscout.androidaps.combo.di

import dagger.Module
import info.nightscout.androidaps.danars.di.ComboActivitiesModule

@Module(includes = [
    ComboActivitiesModule::class
])
open class ComboModule