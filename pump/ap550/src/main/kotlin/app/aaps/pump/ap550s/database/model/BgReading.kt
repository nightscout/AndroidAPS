package app.aaps.pump.ap550s.database.model

class BgReading {

    var id: Int = 0
    var date: String? = null
    var value: String? = null

    constructor()

    constructor(id: Int, date: String?, value: String?) {
        this.id = id
        this.date = date
        this.value = value
    }

    companion object {

        const val TABLE_NAME: String = "BgReadings"

        const val COLUMN_ID: String = "_id"
        const val COLUMN_DATE: String = "date"
        const val COLUMN_VALUE: String = "value"
        const val COLUMN_ISVALID: String = "isValid"

        // Create table SQL query
        const val CREATE_TABLE: String = ("CREATE TABLE " + TABLE_NAME + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_DATE + " TEXT,"
            + COLUMN_VALUE + " DATETIME DEFAULT COLUMN_VALUE"
            + ")")
    }
}
