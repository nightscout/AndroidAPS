package info.nightscout.androidaps.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
open class DatabaseModule {

    @DbFileName
    @Provides
    fun dbFileName() = "androidaps.db"

    @Provides
    @Singleton
    internal fun provideAppDatabase(context: Context, @DbFileName fileName: String) =
        Room
            .databaseBuilder(context, AppDatabase::class.java, fileName)
            .addMigrations(migration5to6)
            .addMigrations(migration6to7)
            .fallbackToDestructiveMigration()
            .build()

    @Qualifier
    annotation class DbFileName

    private val migration5to6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS userEntry")
            database.execSQL("CREATE TABLE userEntry (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `action` TEXT NOT NULL, `s` TEXT NOT NULL, `values` TEXT NOT NULL)")
        }
    }

    private val migration6to7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS foods (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `name` TEXT NOT NULL, `category` TEXT, `subCategory` TEXT, `portion` REAL NOT NULL, `carbs` INTEGER NOT NULL, `fat` INTEGER, `protein` INTEGER, `energy` INTEGER, `unit` TEXT NOT NULL, `gi` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `foods`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        }
    }
}