package app.aaps.core.data.model

fun SourceSensor.advancedFilteringSupported(): Boolean = this in ADVANCED_FILTERING_SENSORS

/**
 * True when a long flat line from this source can mean a stuck sensor and not a steady glucose, so
 * `BgQualityCheckPlugin` runs its flat check and OpenAPS gets `flatBGsDetected`.
 *
 * Libre 1 is the original case. [SourceSensor.UNKNOWN] is here because a source we do not recognise
 * is also a source we cannot vouch for, and cloud followers that do not name their real sensor are
 * left unmapped on purpose so they arrive as unknown - see [SourceSensor.fromString].
 * [SourceSensor.MM_UNKNOWN] names its platform but still comes over a cloud link, where a
 * repeated value is possible, so it keeps the check as well.
 */
fun SourceSensor.needsFlatBgCheck(): Boolean = this in FLAT_BG_CHECK_SENSORS

private val ADVANCED_FILTERING_SENSORS = setOf(
    // Every Dexcom sensor still sold filters internally. This covers the Share link too, where the
    // model is not known but the vendor is.
    SourceSensor.DEXCOM_UNKNOWN,
    SourceSensor.DEXCOM_G6,
    SourceSensor.DEXCOM_G7,
    SourceSensor.LIBRE_2,
    SourceSensor.LIBRE_3,
    SourceSensor.SYAI_TAG,
    SourceSensor.RANDOM,
)

private val FLAT_BG_CHECK_SENSORS = setOf(
    SourceSensor.LIBRE_1,
    // Medtronic with no model: a Guardian needs calibration, and a CareLink link can repeat a value.
    SourceSensor.MM_UNKNOWN,
    // Juggluco sends the same name for a Glutec sensor read directly and for one relayed by the
    // Glutec cloud, so a reading may have come over a link where a repeated value is possible.
    SourceSensor.GLUTEC,
    SourceSensor.AIDEX,
    SourceSensor.AIDEX_X,
    SourceSensor.UNKNOWN,
)
