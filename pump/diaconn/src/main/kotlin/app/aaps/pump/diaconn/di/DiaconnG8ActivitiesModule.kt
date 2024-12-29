package app.aaps.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Fragment
import app.aaps.pump.diaconn.activities.DiaconnG8BLEScanActivity
import app.aaps.pump.diaconn.activities.DiaconnG8HistoryActivity
import app.aaps.pump.diaconn.activities.DiaconnG8UserOptionsActivity

@Module
@Suppress("unused")
interface DiaconnG8ActivitiesModule {
    @ContributesAndroidInjector fun contributesDiaconnG8Fragment(): DiaconnG8Fragment
    @ContributesAndroidInjector fun contributesDiaconnG8HistoryActivity(): DiaconnG8HistoryActivity
    @ContributesAndroidInjector fun contributesDiaconnG8UserOptionsActivity(): DiaconnG8UserOptionsActivity
    @ContributesAndroidInjector fun contributesDiaconnG8BLEScanActivity(): DiaconnG8BLEScanActivity
}