package com.microtechmd.equil.di

import android.content.Context
import com.microtechmd.equil.data.database.EquilHistoryDatabase
import com.microtechmd.equil.data.database.EquilHistoryRecordDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@Suppress("unused")
class EquilHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): EquilHistoryDatabase = EquilHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(danaHistoryDatabase: EquilHistoryDatabase): EquilHistoryRecordDao =
        danaHistoryDatabase.historyRecordDao()

}
