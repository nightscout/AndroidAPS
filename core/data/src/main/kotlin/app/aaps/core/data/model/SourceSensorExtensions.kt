package app.aaps.core.data.model

fun SourceSensor.advancedFilteringSupported(): Boolean = this in ADVANCED_FILTERING_SENSORS

private val ADVANCED_FILTERING_SENSORS = setOf(
    SourceSensor.DEXCOM_NATIVE_UNKNOWN,
    SourceSensor.DEXCOM_G6_NATIVE,
    SourceSensor.DEXCOM_G7_NATIVE,
    SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
    SourceSensor.DEXCOM_G7_NATIVE_XDRIP,
    SourceSensor.DEXCOM_G7_XDRIP,
    SourceSensor.LIBRE_2,
    SourceSensor.LIBRE_2_NATIVE,
    SourceSensor.LIBRE_3,
    SourceSensor.SYAI_TAG,
    SourceSensor.RANDOM,
    SourceSensor.EVERSENSE_365,
    SourceSensor.EVERSENSE_E3,
)
