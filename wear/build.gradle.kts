import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.compose.compiler)
    id("com.android.application")
    kotlin("plugin.serialization")
    alias(libs.plugins.metro)
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
}

repositories {
    mavenCentral()
    google()
}

// `--exclude=ios-testflight-*` for the same reason as in `:app` - the tag names one past iOS
// submission, and `git describe` would otherwise report it however far away it is.
fun generateGitBuild(): String {
    try {
        val processBuilder = ProcessBuilder("git", "describe", "--always", "--exclude=ios-testflight-*")
        val output = File.createTempFile("git-build", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
    return stringBuilder.toString()
}


android {
    namespace = "app.aaps.wear"

    defaultConfig {
        minSdk = Versions.wearMinSdk
        targetSdk = Versions.wearTargetSdk

        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            // Disable androidTest coverage, since it performs offline coverage
            // instrumentation and that causes online (JavaAgent) instrumentation
            // to fail in this project.
            enableAndroidTestCoverage = false
        }
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "app_name", "AAPS")
            resValue("string", "label_actions_activity", "AAPS")
            resValue("color", "flavor_header_color", "#B0BEC5")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "app_name", "Pumpcontrol")
            resValue("string", "label_actions_activity", "Pumpcontrol")
            resValue("color", "flavor_header_color", "#B0BEC5")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient")
            resValue("string", "label_actions_activity", "AAPSClient")
            resValue("color", "flavor_header_color", "#E8C50C")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            resValue("string", "label_actions_activity", "AAPSClient2")
            resValue("color", "flavor_header_color", "#0FBBE0")
            versionName = Versions.appVersion + "-aapsclient2"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
        }
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient3")
            resValue("string", "label_actions_activity", "AAPSClient3")
            resValue("color", "flavor_header_color", "#64E86A")
            versionName = Versions.appVersion + "-aapsclient3"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_greenowl"
        }
    }
    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true
    }
}

allprojects {
    repositories {
    }
}

/**
 * Validates the Watch Face Push face APKs (built by :wear:watchfacepush, one per face) with
 * Google's offline validator and embeds each APK plus its validation token into this variant's
 * assets under `watchfacepush/`, as `<face>.apk` and `<face>_token.txt`. The token is a hash
 * over the exact APK bytes, so it must be regenerated on every face build — never hardcoded.
 *
 * Both faces are embedded although only one is ever installed: Watch Face Push gives an app one
 * slot, and the wear app fills it with the face the wearer selected (see `WatchFacePushHelper`).
 */
abstract class EmbedWatchFaceTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    /** Resolved artifact of :wear:watchfacepush — the APK output directory of the `wfs` face */
    @get:InputFiles
    abstract val wfsApkDir: ConfigurableFileCollection

    /** Resolved artifact of :wear:watchfacepush — the APK output directory of the `cwf` face */
    @get:InputFiles
    abstract val cwfApkDir: ConfigurableFileCollection

    @get:Input
    abstract val clientPackageName: Property<String>

    @get:Classpath
    abstract val validatorClasspath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val assetDir = outputDir.get().asFile.resolve("watchfacepush")
        assetDir.deleteRecursively()
        assetDir.mkdirs()
        embed("wfs", wfsApkDir, assetDir)
        embed("cwf", cwfApkDir, assetDir)
    }

    private fun embed(face: String, apkDir: ConfigurableFileCollection, assetDir: File) {
        val watchFaceApk = apkDir.asFileTree.files.singleOrNull { it.extension == "apk" }
            ?: throw GradleException("Expected exactly one $face watch face APK in ${apkDir.files}")
        val stdout = ByteArrayOutputStream()
        execOperations.javaexec {
            classpath = validatorClasspath
            mainClass.set("com.google.android.wearable.watchface.validator.cli.DwfValidation")
            args(
                "--apk_path=${watchFaceApk.absolutePath}",
                "--package_name=${clientPackageName.get()}"
            )
            standardOutput = stdout
        }
        val output = stdout.toString()
        val token = Regex("generated token: (\\S+)").find(output)?.groupValues?.get(1)
            ?: throw GradleException("Watch face validation of the $face face did not produce a token:\n$output")
        watchFaceApk.copyTo(assetDir.resolve("$face.apk"), overwrite = true)
        assetDir.resolve("${face}_token.txt").writeText(token)
    }
}

val watchFacePushValidator: Configuration = configurations.create("watchFacePushValidator") {
    isCanBeConsumed = false
}

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val flavor = variant.flavorName ?: return@onVariants
        val flavorCap = flavor.replaceFirstChar { it.uppercase() }
        val variantCap = variant.name.replaceFirstChar { it.uppercase() }
        // The faces are always embedded from their release build (signed with the debug key in
        // the face module) — the wear app's own build type does not change the face APKs.
        // Consumed as artifact configurations so the producing tasks are wired in automatically,
        // one per face: the face module names them watchfaceApk<Flavor><Face>.
        val faceApkConfigurations = listOf("wfs", "cwf").associateWith { face ->
            val faceCap = face.replaceFirstChar { it.uppercase() }
            val configuration = configurations.create("watchFaceApk$variantCap$faceCap") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            dependencies.add(
                configuration.name,
                dependencies.project(mapOf("path" to ":wear:watchfacepush", "configuration" to "watchfaceApk$flavorCap$faceCap"))
            )
            configuration
        }
        val taskProvider = project.tasks.register(
            "embed${variantCap}WatchFace",
            EmbedWatchFaceTask::class.java
        ) {
            wfsApkDir.from(faceApkConfigurations.getValue("wfs"))
            cwfApkDir.from(faceApkConfigurations.getValue("cwf"))
            clientPackageName.set(variant.applicationId)
            validatorClasspath.from(watchFacePushValidator)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(taskProvider, EmbedWatchFaceTask::outputDir)
    }
}


dependencies {
    watchFacePushValidator(libs.com.google.watchface.validator.push.cli)

    implementation(project(":shared:impl"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:data"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.legacy.support)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.watchface)
    implementation(libs.androidx.wear.watchfacepush)
    implementation(libs.androidx.wear.watchface.complications.data)
    implementation(libs.androidx.wear.watchface.complications.datasource)
    implementation(libs.androidx.wear.watchface.complications.datasource.ktx)
    implementation(libs.androidx.wear.watchface.complications)
    implementation(libs.androidx.wear.watchface.complications.rendering)
    implementation(libs.androidx.wear.watchface.editor)
    implementation(libs.androidx.wear.watchface.client)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)

    implementation(libs.com.google.android.gms.playservices.wearable)
    implementation(files("${rootDir}/wear/libs/hellocharts-library-1.5.8.aar"))

    // Declared here rather than inherited: :shared:impl used to export it, and stopped when it became

    // Robolectric lets a few Android-coupled unit tests (Intent/Build) run on the JVM. It is a JUnit4
    // runner, so the vintage engine bridges those tests onto the JUnit Platform alongside the Jupiter tests.
    testImplementation(libs.org.robolectric)
    testRuntimeOnly(libs.org.junit.vintage.engine)
}
