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
 * Code-free Watch Face Format (WFF) faces, installed at runtime by the wear app through the
 * Watch Face Push API (Wear OS 6+). Watch Face Push requires the face package to be
 * `<client app package>.watchfacepush.<face id>`, so every wear flavor gets its own face APKs.
 *
 * Two faces are built, one per `face` flavor, and the wear app embeds both. Watch Face Push gives
 * an app one slot, so only one of them is installed at a time: the wearer picks which one in the
 * phone's wear settings, and the wear app swaps the slot.
 *
 * - `wfs`: the face designed in Watch Face Studio (AAPS V4.wfs), a layout of AAPS complications.
 *   To update the design: re-export from WFS, then regenerate `src/wfs/template/watchface.xml`
 *   from the exported APK's `res/raw/watchface.xml`, re-inserting the AAPS_WEAR_APP_ID
 *   DefaultProviderPolicy entries.
 * - `cwf`: the document that shows the Custom watchface as a full-screen image complication, so
 *   the wearer's own zip design reaches a watch whose firmware no longer runs code-based faces.
 *   Hand-written, see `_docs/CWF_WFF_Prompt.md`.
 *
 * Each face keeps its own template, preview pictures, `watch_face_info.xml` and strings under
 * `src/<face>/`; only the manifest and the shape declarations are shared in `src/main/`.
 */
android {
    enableKotlin = false
    namespace = "info.nightscout.androidaps.watchfacepush"
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
            // The watch face IS resources — never shrink them. This APK has no code, so the
            // manifest is the shrinker's whole root set, and it does not name the face: Watch
            // Face Format finds res/xml/watch_face.xml by convention. With shrinking on, the
            // face would be stripped.
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // "standard" first, so a variant reads as the wear flavor it belongs to: fullWfsRelease.
    // The standard flavor sets the client app id, the face flavor appends the face suffix that
    // Watch Face Push requires.
    flavorDimensions.addAll(listOf("standard", "face"))
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPS")
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "watchface_name", "Pumpcontrol")
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPSClient")
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPSClient2")
        }
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3"
            dimension = "standard"
            resValue("string", "watchface_name", "AAPSClient3")
        }
        create("wfs") {
            dimension = "face"
            applicationIdSuffix = ".watchfacepush.wfs"
        }
        create("cwf") {
            isDefault = true
            dimension = "face"
            applicationIdSuffix = ".watchfacepush.cwf"
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
 * Takes `android:testOnly` back out of the face's manifest.
 *
 * Android Studio passes `-Pandroid.injected.testOnly=true` when it builds to Run or Install, and it
 * applies to **every** application module in the build - this one included. Studio installs the app
 * it deploys with `adb install -t`, so the flag never shows there. This APK is different: it is
 * installed at runtime by Watch Face Push, which uses the ordinary route and refuses a test-only
 * APK - reporting only "error code 1", with the watch left on its default face and nothing in the
 * logs to say why. It cost an evening once.
 */
abstract class StripTestOnlyTask : DefaultTask() {

    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun strip() {
        val text = mergedManifest.get().asFile.readText()
        // The whole attribute, quotes included - leaving the closing quote behind would break the XML
        updatedManifest.get().asFile.writeText(text.replace(Regex("\\s*android:testOnly=\"[^\"]*\""), ""))
    }
}

/**
 * Generates `res/raw/watchface.xml` for a variant by substituting the wear app's application id
 * into the DefaultProviderPolicy entries of the face's template, so the face's complication slots
 * default to the AAPS complications of the matching wear flavor.
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
        val face = variant.productFlavors.first { (dimension, _) -> dimension == "face" }.second
        val taskName = "generate${variant.name.replaceFirstChar { it.uppercase() }}WatchFaceRes"
        val taskProvider = project.tasks.register(taskName, GenerateWatchFaceResTask::class.java) {
            templateFile.set(project.layout.projectDirectory.file("src/$face/template/watchface.xml"))
            // The wear app's id is everything before the face suffix
            providerAppId.set(variant.applicationId.map { appId -> appId.substringBefore(".watchfacepush.") })
        }
        variant.sources.res?.addGeneratedSourceDirectory(taskProvider, GenerateWatchFaceResTask::outputDir)

        // Before the APK is taken below: the flag would otherwise travel into the wear app's
        // assets and make the face impossible to install on the watch.
        val stripProvider = project.tasks.register(
            "strip${variant.name.replaceFirstChar { it.uppercase() }}TestOnly",
            StripTestOnlyTask::class.java
        )
        variant.artifacts.use(stripProvider)
            .wiredWithFiles(StripTestOnlyTask::mergedManifest, StripTestOnlyTask::updatedManifest)
            .toTransform(SingleArtifact.MERGED_MANIFEST)

        // Expose the release APK directory as an outgoing artifact so the wear app can consume it
        // with a real producer→consumer dependency (a hardcoded path would fail Gradle's
        // input-file validation before the APK is built). One configuration per wear flavor and
        // face: watchfaceApkFullWfs, watchfaceApkFullCwf, ...
        if (variant.buildType == "release") {
            val outgoing = configurations.create("watchfaceApk${variant.flavorName!!.replaceFirstChar { it.uppercase() }}") {
                isCanBeConsumed = true
                isCanBeResolved = false
            }
            artifacts.add(outgoing.name, variant.artifacts.get(SingleArtifact.APK))
        }
    }
}
