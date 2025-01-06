import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object Versions {

    const val appVersion = "3.3.1.0"
    const val versionCode = 1500

    const val ndkVersion = "21.1.6352462"

    const val compileSdk = 35
    const val minSdk = 30
    const val targetSdk = 30
    const val wearMinSdk = 28
    const val wearTargetSdk = 29

    val javaVersion = JavaVersion.VERSION_21
    val jvmTarget = JvmTarget.JVM_21
    const val jacoco = "0.8.11"
}
