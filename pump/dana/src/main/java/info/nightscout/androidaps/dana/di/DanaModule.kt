package info.nightscout.androidaps.dana.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.dana.DanaFragment
import info.nightscout.androidaps.dana.activities.DanaHistoryActivity
import info.nightscout.androidaps.dana.activities.DanaUserOptionsActivity
import info.nightscout.androidaps.dana.database.DanaHistoryDatabase
import info.nightscout.androidaps.dana.database.DanaHistoryRecordDao
import javax.inject.Singleton

@Module
@Suppress("unused")
abstract class DanaModule {

    @ContributesAndroidInjector abstract fun contributesDanaRFragment(): DanaFragment
    @ContributesAndroidInjector abstract fun contributeDanaRHistoryActivity(): DanaHistoryActivity
    @ContributesAndroidInjector abstract fun contributeDanaRUserOptionsActivity(): DanaUserOptionsActivity

}