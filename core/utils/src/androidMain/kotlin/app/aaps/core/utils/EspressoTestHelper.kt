package app.aaps.core.utils

@Synchronized
fun isRunningTest(): Boolean {
    return try {
        Class.forName("androidx.test.espresso.Espresso")
        true
    } catch (_: Throwable) {
        // Not running under Espresso. Catch Throwable (not just ClassNotFoundException) because
        // androidx.compose.ui:ui-test-junit4 drags Espresso onto the JVM unit-test classpath, where
        // the class IS found but its static initializer fails off-device (ExceptionInInitializerError).
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
