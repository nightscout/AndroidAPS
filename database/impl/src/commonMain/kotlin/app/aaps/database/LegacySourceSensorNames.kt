package app.aaps.database

import app.aaps.database.entities.GlucoseValue

/**
 * Names that older versions stored in the `sourceSensor` column, and the entry each one means now.
 *
 * `GlucoseValue.SourceSensor` used to hold one entry per app or bridge that delivered a reading, so
 * the same sensor had several entries. They were merged into one entry per sensor. Rows written
 * before that keep their old name in the database, and without this map every one of them would read
 * back as `UNKNOWN` - which would not only lose the sensor but also pull months of old readings into
 * the flat check, because an unknown source is treated as one that needs it.
 *
 * Never delete a line from here. It is read only, nothing writes these names any more.
 */
internal val LEGACY_SOURCE_SENSOR_NAMES: Map<String, GlucoseValue.SourceSensor> = mapOf(
    "DEXCOM_NATIVE_UNKNOWN" to GlucoseValue.SourceSensor.DEXCOM_UNKNOWN,
    "DEXCOM_G6_NATIVE" to GlucoseValue.SourceSensor.DEXCOM_G6,
    "DEXCOM_G6_NATIVE_XDRIP" to GlucoseValue.SourceSensor.DEXCOM_G6,
    "DEXCOM_G7_NATIVE" to GlucoseValue.SourceSensor.DEXCOM_G7,
    "DEXCOM_G7_NATIVE_XDRIP" to GlucoseValue.SourceSensor.DEXCOM_G7,
    "DEXCOM_G7_XDRIP" to GlucoseValue.SourceSensor.DEXCOM_G7,
    "LIBRE_1_OTHER" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_NET" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_BLUE" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_PL" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_BLUCON" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_TOMATO" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_RF" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_LIMITTER" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_BUBBLE" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_ATOM" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_1_GLIMP" to GlucoseValue.SourceSensor.LIBRE_1,
    "LIBRE_2_NATIVE" to GlucoseValue.SourceSensor.LIBRE_2,
    "SIBIONIC" to GlucoseValue.SourceSensor.SIBIONIC_UNKNOWN,
    // The same Sinocare sensor, read by an older version of its app.
    "SINO" to GlucoseValue.SourceSensor.SINOCARE,
    // The same sensor under its other brand name.
    "OTTAI" to GlucoseValue.SourceSensor.SYAI_TAG,
)
