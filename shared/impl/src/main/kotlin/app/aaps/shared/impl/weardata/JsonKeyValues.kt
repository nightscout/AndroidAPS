package app.aaps.shared.impl.weardata

enum class JsonKeyValues(val key: String) {
    GONE("gone"),
    VISIBLE("visible"),
    CENTER("center"),
    LEFT("left"),
    RIGHT("right"),
    SANS_SERIF("sans_serif"),
    DEFAULT("default"),
    DEFAULT_BOLD("default_bold"),
    MONOSPACE("monospace"),
    SERIF("serif"),
    ROBOTO_CONDENSED_BOLD("roboto_condensed_bold"),
    ROBOTO_CONDENSED_LIGHT("roboto_condensed_light"),
    ROBOTO_CONDENSED_REGULAR("roboto_condensed_regular"),
    ROBOTO_SLAB_LIGHT("roboto_slab_light"),
    NORMAL("normal"),
    BOLD("bold"),
    BOLD_ITALIC("bold_italic"),
    ITALIC("italic"),
    BGCOLOR("bgColor"),
    SGVLEVEL("sgvLevel"),
    PREF_UNITS("key_units"),
    PREF_DARK("key_dark"),
    PREF_MATCH_DIVIDER("key_match_divider")
}