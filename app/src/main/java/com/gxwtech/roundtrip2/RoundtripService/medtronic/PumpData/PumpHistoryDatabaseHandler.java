package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.List;

/**
 * Created by geoff on 6/17/16.
 */
public class PumpHistoryDatabaseHandler extends SQLiteOpenHelper {
    private static final String TAG = "PumpHistoryDatabase";
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "RT2PumpHistory";
    private static final String DATABASE_TABLE_entries = "entries";

    public PumpHistoryDatabaseHandler(Context context) {
        super(context,DATABASE_NAME,null,DATABASE_VERSION);
    }
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String CREATE_TABLE_entries = "CREATE TABLE " + DATABASE_TABLE_entries + PumpHistoryDatabaseEntry.getTableInitString();
        sqLiteDatabase.execSQL(CREATE_TABLE_entries);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int old_version, int new_version) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS "+DATABASE_TABLE_entries);
        onCreate(sqLiteDatabase);
    }

    public void addEntry(PumpHistoryDatabaseEntry entry) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = entry.getContentValues();
        db.insert(DATABASE_TABLE_entries, null, values);
        db.close();
    }

    public void addContentValuesList(List<ContentValues> list) {
        SQLiteDatabase db = getWritableDatabase();
        for (ContentValues cvs : list) {
            db.insert(DATABASE_TABLE_entries, null, cvs);
        }
        db.close();
        Log.d(TAG,"Database "+ DATABASE_NAME + " saved");
    }

    public void clearPumpHistoryDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS "+DATABASE_TABLE_entries);
        onCreate(db);
        db.close();
    }
}
