package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTime
import app.aaps.database.entities.interfaces.TraceableDBEntry

@Entity(
    tableName = TABLE_GLUCOSE_VALUES,
    foreignKeys = [ForeignKey(
        entity = GlucoseValue::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("nightscoutId"),
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
    override var utcOffset: Long = defaultUtcOffset(timestamp),
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

    /**
     * One entry per sensor. Names that older versions stored are mapped on read by
     * `Converters.toSourceSensor`, so a row written before the entries were merged keeps its meaning.
     */
    enum class SourceSensor {
        DEXCOM_UNKNOWN,
        DEXCOM_G6,
        DEXCOM_G7,
        LIBRE_1,
        LIBRE_2,
        LIBRE_3,
        MEDTRUM_A6,
        MEDTRUM_UNKNOWN,
        MM_600_SERIES,
        MM_SIMPLERA,
        MM_UNKNOWN,
        SIBIONIC_UNKNOWN,
        SIBIONIC_GS1,
        SIBIONIC_GS3,
        ACCU_CHEK,
        CARESENS_AIR,
        AIDEX,
        AIDEX_X,
        POCTECH_NATIVE,
        GLUNOVO_NATIVE,
        INTELLIGO_NATIVE,
        SINOCARE,
        ANYTIME,
        GLUTEC,
        GLUPRO,
        EVERSENSE,
        SYAI_TAG,
        INSTARA,
        RANDOM,
        UNKNOWN,

        IOB_PREDICTION,
        A_COB_PREDICTION,
        COB_PREDICTION,
        UAM_PREDICTION,
        ZT_PREDICTION,
        ;
    }
}
