package app.aaps.core.utils

@Synchronized
fun isRunningRealPumpTest(): Boolean {
    return try {
        Class.forName("app.aaps.RealPumpTest")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
