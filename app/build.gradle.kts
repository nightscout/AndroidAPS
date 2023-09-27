import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.io.println

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-allopen")
    id("kotlinx-serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

apply(from = "${project.rootDir}/core/main/android_dependencies.gradle")
apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

repositories {
    mavenCentral()
    google()
}

allOpen {
    // allows mocking for classes w/o directly opening them for release builds
    annotation("info.nightscout.androidaps.annotations.OpenForTesting")
}

fun generateGitBuild(): String =
    try {
        ByteArrayOutputStream().let {
            exec {
                commandLine("git", "describe", "--always")
                standardOutput = it
            }
            it.toString().trim()
        }
    } catch (ignored: Exception) {
        "NoGitSystemAvailable"
    }

fun generateGitRemote(): String =
    try {
        ByteArrayOutputStream().let {
            exec {
                commandLine("git", "remote", "get-url", "origin")
                standardOutput = it
            }
            it.toString().trim()
        }
    } catch (ignored: Exception) {
        "NoGitSystemAvailable"
    }

fun generateDate(): String =
    // showing only date prevents app to rebuild everytime
    SimpleDateFormat("yyyy.MM.dd").format(Date())

fun isMaster(): Boolean = !Versions.appVersion.contains("-")

fun gitAvailable(): Boolean =
    try {
        ByteArrayOutputStream().let {
            exec {
                commandLine("git", "--version")
                standardOutput = it
            }
            it.toString().trim().isNotEmpty()
        }
    } catch (ignored: Exception) {
        false // NoGitSystemAvailable
    }

fun allCommitted(): Boolean =
    try {
        ByteArrayOutputStream().let {
            exec {
                commandLine("git", "status", "-s")
                standardOutput = it
            }
            it.toString()
                // ignore all changes done in .idea/codeStyles
                .replace(Regex("(?m)^\\s*(M|A|D|\\?\\?)\\s*.*?\\.idea\\/codeStyles\\/.*?\\s*$"), "")
                // ignore all files added to project dir but not staged/known to GIT
                .replace(Regex("(?m)^\\s*(\\?\\?)\\s*.*?\\s*$"), "")
                .trim().isEmpty()
        }
    } catch (ignored: Exception) {
        false // NoGitSystemAvailable
    }

android {

    namespace = "info.nightscout.androidaps"
    ndkVersion = Versions.ndkVersion

    defaultConfig {
        multiDexEnabled = true
        versionCode = Versions.versionCode
        version = Versions.appVersion
        buildConfigField("String", "VERSION", "\"" + Versions.appVersion + "\"")
        buildConfigField("String", "BUILDVERSION", "\"" + generateGitBuild() + "-" + generateDate() + "\"")
        buildConfigField("String", "REMOTE", "\"" + generateGitRemote() + "\"")
        buildConfigField("String", "HEAD", "\"" + generateGitBuild() + "\"")
        buildConfigField("Boolean", "COMMITTED", allCommitted().toString())
    }

    val dim = "standard"
    flavorDimensions.add(dim)
    productFlavors {
        create("full") {
            applicationId = "info.nightscout.androidaps"
            dimension = dim
            resValue("string", "app_name", "AAPS")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_launcher_round"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = dim
            resValue("string", "app_name", "Pumpcontrol")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
            manifestPlaceholders["appIconRound"] = "@null"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = dim
            resValue("string", "app_name", "AAPSClient")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = dim
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_blueowl"
        }
    }

    dataBinding {   //Deleting it causes a binding error
        enable = true
    }
}

dependencies {
    // in order to use internet"s versions you"d need to enable Jetifier again
    // https://github.com/nightscout/graphview.git
    // https://github.com/nightscout/iconify.git
    implementation(project(":app-wear-shared:shared"))
    implementation(project(":app-wear-shared:shared-impl"))
    implementation(project(":core:main"))
    implementation(project(":core:graph"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:libraries"))
    implementation(project(":core:ns-sdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":ui"))
    implementation(project(":plugins:aps"))
    implementation(project(":plugins:automation"))
    implementation(project(":plugins:configuration"))
    implementation(project(":plugins:constraints"))
    implementation(project(":plugins:insulin"))
    implementation(project(":plugins:main"))
    implementation(project(":plugins:sensitivity"))
    implementation(project(":plugins:smoothing"))
    implementation(project(":plugins:source"))
    implementation(project(":plugins:sync"))
    implementation(project(":implementation"))
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":pump:combo"))
    implementation(project(":pump:combov2"))
    implementation(project(":pump:dana"))
    implementation(project(":pump:danars"))
    implementation(project(":pump:danar"))
    implementation(project(":pump:diaconn"))
    implementation(project(":pump:eopatch"))
    implementation(project(":pump:medtrum"))
    implementation(project(":insight"))
    implementation(project(":pump:medtronic"))
    implementation(project(":pump:pump-common"))
    implementation(project(":pump:pump-core"))
    implementation(project(":pump:omnipod-common"))
    implementation(project(":pump:omnipod-eros"))
    implementation(project(":pump:omnipod-dash"))
    implementation(project(":pump:rileylink"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))

    testImplementation(project(":app-wear-shared:shared-tests"))

    //implementation fileTree(include = listOf("*.jar"), dir = "libs")

    /* Dagger2 - We are going to use dagger.android which includes
     * support for Activity and fragment injection so we need to include
     * the following dependencies */
    kapt(Libs.Dagger.androidProcesssor)
    kapt(Libs.Dagger.compiler)

    // MainApp
    api(Libs.Rx.rxDogTag)

}

apply(from = "${project.rootDir}/core/main/test_dependencies.gradle")

println("--------------")
println("isMaster: ${isMaster()}")
println("gitAvailable: ${gitAvailable()}")
println("allCommitted: ${allCommitted()}")
println("--------------")
if (isMaster() && !gitAvailable()) {
    throw GradleException("GIT system is not available. On Windows try to run Android Studio as an Administrator. Check if GIT is installed and Studio have permissions to use it")
}
if (isMaster() && !allCommitted()) {
    throw GradleException("There are uncommitted changes. Clone sources again as described in wiki and do not allow gradle update")
}
