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
