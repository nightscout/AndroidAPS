package info.nightscout.androidaps.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Fragment
import info.nightscout.androidaps.diaconn.activities.DiaconnG8BLEScanActivity
import info.nightscout.androidaps.diaconn.activities.DiaconnG8HistoryActivity
import info.nightscout.androidaps.diaconn.activities.DiaconnG8UserOptionsActivity

@Module
@Suppress("unused")
abstract class DiaconnG8ActivitiesModule {
    @ContributesAndroidInjector abstract fun contributesDiaconnG8Fragment(): DiaconnG8Fragment
    @ContributesAndroidInjector abstract fun contributesDiaconnG8HistoryActivity(): DiaconnG8HistoryActivity
    @ContributesAndroidInjector abstract fun contributesDiaconnG8UserOptionsActivity(): DiaconnG8UserOptionsActivity
    @ContributesAndroidInjector abstract fun contributesDiaconnG8BLEScanActivity(): DiaconnG8BLEScanActivity
}