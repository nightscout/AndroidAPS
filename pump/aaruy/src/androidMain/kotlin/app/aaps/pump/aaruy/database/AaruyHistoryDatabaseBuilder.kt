package app.aaps.pump.aaruy.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Opens the pump log on Android, in the file it always had. The bundled SQLite driver reads the file the framework
 * SQLite wrote before, so nothing of the stored log is lost.
 */
internal fun buildAaruyHistoryDatabase(context: Context): AaruyHistoryDatabase =
    Room.databaseBuilder(context.applicationContext, AaruyHistoryDatabase::class.java, AARUY_DATABASE_FILE)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*AaruyHistoryDatabase.migrations)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
