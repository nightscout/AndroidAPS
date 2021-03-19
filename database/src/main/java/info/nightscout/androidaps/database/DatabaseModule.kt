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
}