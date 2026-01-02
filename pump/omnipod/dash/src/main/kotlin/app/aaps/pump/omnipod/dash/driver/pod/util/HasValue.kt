package app.aaps.pump.omnipod.dash.driver.pod.util

interface HasValue {

    val value: Byte
}

inline fun <reified T> byValue(value: Byte, default: T): T where T : Enum<T>, T : HasValue {
    return enumValues<T>().firstOrNull { it.value == value } ?: default
}
