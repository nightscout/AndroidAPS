package app.aaps.pump.ap550s.di

import android.content.Context
import app.aaps.pump.ap550.protocol.comm.Ap550LinkCodec
import app.aaps.pump.ap550.protocol.comm.Ap550MessageFactory
import app.aaps.pump.ap550.protocol.comm.MessageHashTableAP550
import app.aaps.pump.ap550.protocol.logging.Ap550Sdk
import app.aaps.pump.ap550s.database.Ap550HistoryDatabase
import app.aaps.pump.ap550s.database.Ap550HistoryRecordDao
import app.aaps.pump.ap550s.utils.ConnectUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The protocol SDK objects this driver needs, and its own history database.
 *
 * Everything here is a single instance for the whole app: the message factory and the dispatch table
 * are stateless lookups, and [ConnectUtil] holds the connection history that the plugin and the
 * execution service both read.
 *
 * The history database lives in this module rather than in the app, for the same reason the Dana one
 * does - nothing here needs the app, so the app names no AP550 type.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AP550Bindings {

    // ----- protocol SDK -----

    @Provides
    @SingleIn(AppScope::class)
    fun provideAp550MessageFactory(): Ap550MessageFactory = Ap550MessageFactory(Ap550Sdk.logger)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMessageHashTable(): MessageHashTableAP550 = MessageHashTableAP550(Ap550Sdk.logger)

    // Takes the table above rather than building its own, so the codec and the service dispatch
    // through the same instance.
    @Provides
    @SingleIn(AppScope::class)
    fun provideAp550LinkCodec(hashTable: MessageHashTableAP550): Ap550LinkCodec =
        Ap550LinkCodec(Ap550Sdk.logger, hashTable)

    @Provides
    @SingleIn(AppScope::class)
    fun provideConnectUtil(): ConnectUtil = ConnectUtil()

    // ----- history database -----

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): Ap550HistoryDatabase = Ap550HistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryRecordDao(ap550HistoryDatabase: Ap550HistoryDatabase): Ap550HistoryRecordDao =
        ap550HistoryDatabase.historyRecordDao()
}