package app.aaps.pump.ap550s.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.aaps.pump.ap550s.database.model.BgReading

/**
 * Created by lijia on 22/10/22.
 */
class DatabaseHelper(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Creating Tables
    override fun onCreate(db: SQLiteDatabase) {
        // create notes table
        //db.execSQL(Note.CREATE_TABLE);
    }

    // Upgrading database
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop older table if existed
        //db.execSQL("DROP TABLE IF EXISTS " + Note.TABLE_NAME);

        // Create tables again
        //onCreate(db);
    }

    fun getNote(id: Long): BgReading? {
        // get readable database as we are not inserting anything
        val db = this.readableDatabase

        /*Cursor cursor = db.query(Note.TABLE_NAME,
                new String[]{Note.COLUMN_ID, Note.COLUMN_NOTE, Note.COLUMN_TIMESTAMP},
                Note.COLUMN_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null, null);

        if (cursor != null)
            cursor.moveToFirst();

        // prepare note object
        Note note = new Note(
                cursor.getInt(cursor.getColumnIndex(Note.COLUMN_ID)),
                cursor.getString(cursor.getColumnIndex(Note.COLUMN_NOTE)),
                cursor.getString(cursor.getColumnIndex(Note.COLUMN_TIMESTAMP)));

        // close the db connection
        cursor.close();*/
        return null
    }

    @get:SuppressLint("Range") val bgReading: List<Any>
        get() {
            val bgReadings: MutableList<BgReading> = ArrayList<BgReading>()

            // Select All Query
            val selectQuery = "SELECT  * FROM " + DATABASE_BGREADINGS + " ORDER BY " +
                "value" + " DESC"
            val db = this.writableDatabase
            val cursor = db.rawQuery(selectQuery, null)

            // looping through all rows and adding to list
            if (cursor.moveToFirst() && cursor.count > 0) {
                do {
                    val bgReading: BgReading = BgReading()
                   // bgReading.setId(cursor.getInt(cursor.getColumnIndex(BgReading.COLUMN_ID)))
                    //bgReading.setDate(cursor.getString(cursor.getColumnIndex(BgReading.COLUMN_DATE)))
                    //bgReading.setValue(cursor.getString(cursor.getColumnIndex(BgReading.COLUMN_VALUE)))

                    bgReadings.add(bgReading)
                } while (cursor.moveToNext())
            }

            // close db connection
            db.close()

            // return notes list
            return bgReadings
        }

    val notesCount: Int
        get() {
            //String countQuery = "SELECT  * FROM " + Note.TABLE_NAME;
            val countQuery = "SELECT  * FROM " + 1
            val db = this.readableDatabase
            val cursor = db.rawQuery(countQuery, null)

            val count = cursor.count
            cursor.close()

            // return count
            return count
        } /*public int updateNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(Note.COLUMN_NOTE, note.getNote());

        // updating row
        return db.update(Note.TABLE_NAME, values, Note.COLUMN_ID + " = ?",
                new String[]{String.valueOf(note.getId())});
    }

    public void deleteNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(Note.TABLE_NAME, Note.COLUMN_ID + " = ?",
                new String[]{String.valueOf(note.getId())});
        db.close();
    }*/

    companion object {

        // Database Version
        private const val DATABASE_VERSION = 13

        // Database Name
        private const val DATABASE_NAME = "AndroidAPSDb"
        private const val DATABASE_BGREADINGS = "BgReadings"
    }
}
