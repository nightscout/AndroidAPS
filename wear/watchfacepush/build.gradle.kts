import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
}

repositories {
    mavenCentral()
    google()
}

/**
 * Code-free Watch Face Format (WFF) face, installed at runtime by the wear app through the
 * Watch Face Push API (Wear OS 6+). Watch Face Push requires the face package to be
 * `<client app package>.watchfacepush.<face id>`, so every wear flavor gets its own face APK.
 *
 * The face source is exported from the Watch Face Studio project (AAPS V4.wfs). To update the
 * design: re-export from WFS, then regenerate `template/watchface.xml` from the exported APK's
 * `res/raw/watchface.xml`, re-inserting the AAPS_WEAR_APP_ID DefaultProviderPolicy entries.
 */
android {
    enableKotlin = false
    namespace = "info.nightscout.androidaps.watchfacepush.aapsv4"
    compileSdk = Versions.compileSdk

    defaultConfig {
        minSdk = 33
        targetSdk = 34
        versionCode = Versions.versionCode
        versionName = Versions.appVersion
    }

    signingConfigs {
        // The face APK is not distributed through a store — it is embedded in the wear app and
        // installed via Watch Face Push, which only requires that the APK carries *a* signature.
        // Signing release with the debug key keeps user builds free of extra signing setup.
        getByName("debug")
    }
    buildTypes {
        release {
            // R8 with no entry points strips the generated R class entirely, so no classes.dex is
            // packaged — Watch Face Push validation rejects any APK containing code (same recipe
            // as Google's WatchFaceFormat samples)
            isMinifyEnabled = true
            // The watch face IS resources — never shrink them
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps.watchfacepush.aapsv4"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPS")
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol.watchfacepush.aapsv4"
            dimension = "standard"
            resValue("string", "watchface_name", "Pumpcontrol")
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient.watchfacepush.aapsv4"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPSClient")
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2.watchfacepush.aapsv4"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPSClient2")
        }
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3.watchfacepush.aapsv4"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPSClient3")
        }
    }
    buildFeatures {
        resValues = true
    }
    packaging {
        resources.excludes += setOf("kotlin/**", "META-INF/*.version", "META-INF/*.kotlin_module")
    }
    lint {
        // Code-free resource-only APK — nothing to lint, and lintVital would otherwise run on
        // every wear release build
        checkReleaseBuilds = false
    }
}

// The root build wires kotlin-stdlib into every project; this APK must stay code-free
// (Watch Face Push validation rejects any classes.dex or other non-res files), so strip it from
// the app classpaths — ONLY those: a blanket exclude would also break tool classpaths (lint runs
// on Kotlin itself)
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}

/**
 * Generates `res/raw/watchface.xml` for a variant by substituting the wear app's application id
 * into the DefaultProviderPolicy entries of the template, so the face's complication slots default
 * to the AAPS complications of the matching wear flavor.
 */
abstract class GenerateWatchFaceResTask : DefaultTask() {

    @get:InputFile
    abstract val templateFile: RegularFileProperty

    @get:Input
    abstract val providerAppId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val rawDir = outputDir.get().asFile.resolve("raw")
        rawDir.deleteRecursively()
        rawDir.mkdirs()
        val content = templateFile.get().asFile.readText()
            .replace("AAPS_WEAR_APP_ID", providerAppId.get())
        rawDir.resolve("watchface.xml").writeText(content)
    }
}

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val taskName = "generate${variant.name.replaceFirstChar { it.uppercase() }}WatchFaceRes"
        val taskProvider = project.tasks.register(taskName, GenerateWatchFaceResTask::class.java) {
            templateFile.set(project.layout.projectDirectory.file("template/watchface.xml"))
            providerAppId.set(variant.applicationId.map { appId -> appId.removeSuffix(".watchfacepush.aapsv4") })
        }
        variant.sources.res?.addGeneratedSourceDirectory(taskProvider, GenerateWatchFaceResTask::outputDir)

        // Expose the release APK directory as an outgoing artifact so the wear app can consume it
        // with a real producer→consumer dependency (a hardcoded path would fail Gradle's
        // input-file validation before the APK is built)
        if (variant.buildType == "release") {
            val outgoing = configurations.create("watchfaceApk${variant.flavorName!!.replaceFirstChar { it.uppercase() }}") {
                isCanBeConsumed = true
                isCanBeResolved = false
            }
            artifacts.add(outgoing.name, variant.artifacts.get(SingleArtifact.APK))
        }
    }
}
