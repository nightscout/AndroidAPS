import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("ConstPropertyName")
object Versions {

    // Production releases use a numeric-only version so Config.isDev() is false.
    // Increment versionCode for every signed production update.
    const val appVersion = "4.0.0"
    const val versionCode = 1501

    const val compileSdk = 37
    const val minSdk = 31
    const val targetSdk = 35
    const val wearMinSdk = 30
    const val wearTargetSdk = 30

    val javaVersion = JavaVersion.VERSION_21
    val jvmTarget = JvmTarget.JVM_21
    const val jacoco = "0.8.11"
}
