package info.nightscout.androidaps.database.entities

import androidx.room.*
import info.nightscout.androidaps.database.TABLE_BOLUS_CALCULATOR_RESULTS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_BOLUS_CALCULATOR_RESULTS,
        foreignKeys = [ForeignKey(
                entity = BolusCalculatorResult::class,
                parentColumns = ["id"],
                childColumns = ["referenceId"])],
        indices = [Index("referenceId"), Index("timestamp")])
data class BolusCalculatorResult(
    @PrimaryKey(autoGenerate = true)
        override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
        override var interfaceIDs_backing: InterfaceIDs? = null,
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var targetBGLow: Double,
    var targetBGHigh: Double,
    var isf: Double,
    var ic: Double,
    var bolusIOB: Double,
    var wasBolusIOBUsed: Boolean,
    var basalIOB: Double,
    var wasBasalIOBUsed: Boolean,
    var glucoseValue: Double,
    var wasGlucoseUsed: Boolean,
    var glucoseDifference: Double,
    var glucoseInsulin: Double,
    var glucoseTrend: Double,
    var wasTrendUsed: Boolean,
    var trendInsulin: Double,
    var cob: Double,
    var wasCOBUsed: Boolean,
    var cobInsulin: Double,
    var carbs: Double,
    var wereCarbsUsed: Boolean,
    var carbsInsulin: Double,
    var otherCorrection: Double,
    var wasSuperbolusUsed: Boolean,
    var superbolusInsulin: Double,
    var wasTempTargetUsed: Boolean,
    var totalInsulin: Double
) : TraceableDBEntry, DBEntryWithTime