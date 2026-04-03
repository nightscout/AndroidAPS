package app.aaps.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase
import app.aaps.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

// Used in DaggerTestAppComponent (plain Dagger); duplicate AppDatabase binding conflicts with Hilt.
@Module
@DisableInstallInCheck
open class TestDatabaseModule {

    @Provides
    @Singleton
    internal fun provideAppDatabase(context: Context) =
        Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    createCustomIndexes(db)
                }
            })
            .fallbackToDestructiveMigration(false)
            .build()

    private fun createCustomIndexes(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_end` ON `temporaryBasals` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_extendedBoluses_end` ON `extendedBoluses` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryTargets_end` ON `temporaryTargets` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_carbs_end` ON `carbs` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_end` ON `runningModes` (`timestamp` + `duration`)")
    }
}
