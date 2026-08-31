package app.aaps.di.pump

import android.content.Context
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The Dana history database, provided from `:app` rather than from `:pump:dana`.
 *
 * Moved here so `:pump:dana` needs no Dagger processor of its own. The bindings stay Dagger's, because
 * their consumers - the Dana pump plugins - are still Dagger built; only the module's *location* moves.
 * When `:app` comes off Dagger these go with everything else, and by then the consumers are Metro too,
 * so nothing has to be handed across.
 */
@Module
@InstallIn(SingletonComponent::class)
class DanaHistoryModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): DanaHistoryDatabase = DanaHistoryDatabase.build(context)

    @Provides
    @Singleton
    fun provideHistoryRecordDao(danaHistoryDatabase: DanaHistoryDatabase): DanaHistoryRecordDao =
        danaHistoryDatabase.historyRecordDao()
}
