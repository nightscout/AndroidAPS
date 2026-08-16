import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("ConstPropertyName")
object Versions {

    // On change edit aaps-ci.yml
    const val appVersion = "3.4.2.6"
    const val versionCode = 1500

    const val compileSdk = 36
    // minSdk 参数化:默认 31(手机/主流);W527 手表版(Android 11)构建加 -Daaps.minSdk=30
    // 勿改回 const;构建命令示例:./gradlew -Daaps.minSdk=30 assembleRelease
    val minSdk: Int = System.getProperty("aaps.minSdk")?.toIntOrNull() ?: 31
    const val targetSdk = 32
    const val wearMinSdk = 30
    const val wearTargetSdk = 30

    val javaVersion = JavaVersion.VERSION_21
    val jvmTarget = JvmTarget.JVM_21
    const val jacoco = "0.8.11"
}
