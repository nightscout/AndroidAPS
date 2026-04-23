package app.aaps.core.utils

@Synchronized
fun isRunningTest(): Boolean {
    return try {
        Class.forName("androidx.test.espresso.Espresso")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}

@Synchronized
fun isRunningRealPumpTest(): Boolean {
    return try {
        Class.forName("app.aaps.RealPumpTest")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
