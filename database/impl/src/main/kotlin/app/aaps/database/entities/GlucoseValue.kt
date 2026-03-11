package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTime
import app.aaps.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_GLUCOSE_VALUES,
    foreignKeys = [ForeignKey(
        entity = GlucoseValue::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("sourceSensor"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class GlucoseValue(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var raw: Double?,
    var value: Double,
    var trendArrow: TrendArrow,
    var noise: Double?,
    var sourceSensor: SourceSensor
) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: GlucoseValue): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            raw == other.raw &&
            value == other.value &&
            trendArrow == other.trendArrow &&
            noise == other.noise &&
            sourceSensor == other.sourceSensor

    enum class TrendArrow {
        NONE,
        TRIPLE_UP,
        DOUBLE_UP,
        SINGLE_UP,
        FORTY_FIVE_UP,
        FLAT,
        FORTY_FIVE_DOWN,
        SINGLE_DOWN,
        DOUBLE_DOWN,
        TRIPLE_DOWN
        ;
    }

    enum class SourceSensor {
        DEXCOM_NATIVE_UNKNOWN,
        DEXCOM_G6_NATIVE,
        DEXCOM_G7_NATIVE,
        MEDTRUM_A6,
        DEXCOM_G6_NATIVE_XDRIP,
        DEXCOM_G7_NATIVE_XDRIP,
        DEXCOM_G7_XDRIP,
        LIBRE_1_OTHER,
        LIBRE_1_NET,
        LIBRE_1_BLUE,
        LIBRE_1_PL,
        LIBRE_1_BLUCON,
        LIBRE_1_TOMATO,
        LIBRE_1_RF,
        LIBRE_1_LIMITTER,
        LIBRE_1_BUBBLE,
        LIBRE_1_ATOM,
        LIBRE_1_GLIMP,
        LIBRE_2,
        LIBRE_2_NATIVE,
        LIBRE_3,
        POCTECH_NATIVE,
        GLUNOVO_NATIVE,
        INTELLIGO_NATIVE,
        MM_600_SERIES,
        EVERSENSE_E3,
        EVERSENSE_365,
        AIDEX,
        RANDOM,
        UNKNOWN,
        OTTAI,
        SIBIONIC,
        SINO,
        SYAI_TAG,

        IOB_PREDICTION,
        A_COB_PREDICTION,
        COB_PREDICTION,
        UAM_PREDICTION,
        ZT_PREDICTION,
        ;
    }
}
