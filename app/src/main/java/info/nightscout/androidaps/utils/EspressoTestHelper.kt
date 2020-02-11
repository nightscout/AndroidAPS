package info.nightscout.androidaps.utils

@Synchronized
fun isRunningTest(): Boolean {
    return try {
        Class.forName("androidx.test.espresso.Espresso")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}

@Synchronized
fun isRunningRealPumpTest(): Boolean {
    return try {
        Class.forName("info.nightscout.androidaps.RealPumpTest")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}
