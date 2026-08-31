package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Diaconn history database, provided from `:app` so `:pump:diaconn` needs no Dagger processor. */
@Module
@InstallIn(SingletonComponent::class)
class DiaconnHistoryModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): DiaconnHistoryDatabase = DiaconnHistoryDatabase.build(context)

    @Provides
    @Singleton
    fun provideHistoryRecordDao(diaconnHistoryDatabase: DiaconnHistoryDatabase): DiaconnHistoryRecordDao =
        diaconnHistoryDatabase.historyRecordDao()
}
