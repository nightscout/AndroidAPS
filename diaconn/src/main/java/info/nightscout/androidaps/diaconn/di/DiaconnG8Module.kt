package info.nightscout.androidaps.diaconn.di

import dagger.Module

@Module(includes = [
    DiaconnG8ActivitiesModule::class,
    DiaconnG8ServiceModule::class,
    DiaconnG8PacketModule::class,
    DiaconnHistoryModule::class
])

open class DiaconnG8Module