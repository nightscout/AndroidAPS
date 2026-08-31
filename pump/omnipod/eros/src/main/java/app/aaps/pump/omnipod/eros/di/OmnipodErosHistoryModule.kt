package app.aaps.pump.omnipod.eros.di

import android.content.Context
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class OmnipodErosHistoryModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): ErosHistoryDatabase = ErosHistoryDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(erosHistoryDatabase: ErosHistoryDatabase): ErosHistoryRecordDao =
        erosHistoryDatabase.historyRecordDao()

    // Explicit return type: Metro reads this module through Dagger interop and rejects an inferred one,
    // because the type a binding provides has to be readable without compiling the body.
    @Provides
    @Singleton
    internal fun provideErosHistory(dao: ErosHistoryRecordDao): ErosHistory =
        ErosHistory(dao)

}
