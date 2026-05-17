package app.aaps.ui.compose.careDialog

/**
 * Types of care portal events rendered by the care dialog.
 *
 * WARNING: the ordinal is serialized through [app.aaps.compose.navigation.AppRoute.CareDialog]
 * as an int argument. Reordering values changes the wire format of in-flight navigation
 * arguments — only append new values, never reorder or insert.
 */
enum class CareportalEventType {

    /** A blood glucose check. */
    BGCHECK,

    /** A CGM sensor insertion. */
    SENSOR_INSERT,

    /** A pump battery change. */
    BATTERY_CHANGE,

    /** A general note. */
    NOTE,

    /** An exercise event. */
    EXERCISE,

    /** A question/prompt. */
    QUESTION,

    /** An announcement. */
    ANNOUNCEMENT
}
