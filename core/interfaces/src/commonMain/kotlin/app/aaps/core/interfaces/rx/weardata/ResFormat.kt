package app.aaps.core.interfaces.rx.weardata

const val CUSTOM_VERSION = "2.0"

enum class ResFormat(val extension: String) {
    UNKNOWN(""),
    SVG("svg"),
    JPG("jpg"),
    PNG("png"),
    TTF("ttf"),
    OTF("otf");

    companion object {

        fun fromFileName(fileName: String): ResFormat =
            entries.firstOrNull { it.extension == fileName.substringAfterLast(".").lowercase() } ?: UNKNOWN

    }
}