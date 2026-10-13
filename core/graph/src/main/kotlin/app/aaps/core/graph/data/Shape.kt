package app.aaps.core.graph.data

/**
 * choose a predefined shape to render for
 * each data point.
 * You can also render a custom drawing via [com.jjoe64.graphview.series.PointsGraphSeries.CustomShape]
 */
enum class Shape {

    BG,
    PREDICTION,
    TRIANGLE,
    RECTANGLE,
    BOLUS,
    CARBS,
    SMB,
    EXTENDEDBOLUS,
    PROFILE,
    MBG,
    BGCHECK,
    ANNOUNCEMENT,
    SETTINGS_EXPORT,
    RUNNING_MODE,
    EXERCISE,
    GENERAL,
    GENERAL_WITH_DURATION,
    COB_FAIL_OVER,
    IOB_PREDICTION,
    BUCKETED_BG,
    HEART_RATE,
    STEPS
}
