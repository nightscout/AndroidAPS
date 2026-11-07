package app.aaps.core.interfaces.overview.graph

data class AapsClientStatusData(
    val pump: AapsClientStatusItem? = null,
    val openAps: AapsClientStatusItem? = null,
    val uploader: AapsClientStatusItem? = null
)

data class AapsClientStatusItem(
    val label: String,
    val value: String,
    val level: AapsClientLevel,
    val dialogTitle: String,
    val dialogText: String
)

enum class AapsClientLevel {
    INFO,
    WARN,
    URGENT
}