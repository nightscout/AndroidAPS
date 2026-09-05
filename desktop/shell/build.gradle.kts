import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
plugins {
    kotlin("jvm")
    // Metro, because this module does not only link the shared code, it builds a graph from it.
    alias(libs.plugins.metro)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * The desktop app: Windows, macOS and Linux.
 *
 * The counterpart of `:app` on Android and `:ios:shell` on Apple. It ships no features of its own -
 * it builds the object graph from the shared modules, supplies the seams only a desktop can fill,
 * and shows `app.aaps.appshell.AapsAppRoot`.
 *
 * A plain `kotlin("jvm")` module rather than a multiplatform one: it has exactly one target, and
 * Gradle resolves the `jvm` variant of every multiplatform module it depends on.
 */
/**
 * Which module owns which strings, generated rather than hand written.
 *
 * The list is `StringOwnerModules.ALL`, shared with `:app` and `:ios:shell`, so a module added to
 * the build cannot be missing from one platform and present on another. That is not theoretical:
 * this shell had five of sixteen and rendered plugin names as string names until it was run.
 */

/** The same build stamp `:app` puts in BuildConfig.BUILDVERSION: short commit and date. */
fun buildStamp(): String {
    val commit = try {
        val out = File.createTempFile("git-build", "")
        ProcessBuilder("git", "describe", "--always", "--abbrev=7").redirectOutput(out).start().waitFor()
        out.readText().trim()
    } catch (_: Exception) {
        "NoGitSystemAvailable"
    }
    return "$commit-" + SimpleDateFormat("yyyy.MM.dd").format(Date())
}
/**
 * The version this build reports, from the same constant the Android app uses.
 *
 * Without it the About dialog showed a hardcoded placeholder, which is worse than showing nothing:
 * a user quoting it in a bug report names a build that does not exist.
 */
/**
 * The app icon, copied from the one place it lives rather than checked in twice.
 *
 * All of them, because the choice is per flavour: `IconsProviderImplementation` maps client 1, 2 and
 * 3, pump control and the master build to different icons, and the desktop follows the same rule at
 * runtime from `Config`. Copying at build time keeps a single source, so a redrawn icon reaches the
 * desktop without anyone remembering to re-export it.
 */
val copyAppIcon = tasks.register<Copy>("copyDesktopAppIcon") {
    from(rootProject.file("core/ui/src/androidMain/res/mipmap-xxxhdpi")) {
        include("ic_launcher.png", "ic_yellowowl.png", "ic_blueowl.png", "ic_greenowl.png", "ic_pumpcontrol.png")
    }
    into(layout.buildDirectory.dir("generated/icon/icons"))
}

val generateBuildInfo = tasks.register<GenerateBuildInfoTask>("generateDesktopBuildInfo") {
    version.set(Versions.appVersion)
    buildStamp.set(buildStamp())
    platform.set("Desktop")
    packageName.set("app.aaps.desktop.shell.config")
    outputDir.set(layout.buildDirectory.dir("generated/buildInfo"))
}

val generateStringOwners = tasks.register<GenerateStringOwnerRegistryTask>("generateDesktopStringOwners") {
    owners.set(StringOwnerModules.ALL)
    packageName.set("app.aaps.desktop.shell.di")
    objectName.set("GeneratedStringOwners")
    // Text, not resource ids: a desktop JVM has no AAPT table to resolve an id against.
    useResourceIds.set(false)
    outputDir.set(layout.buildDirectory.dir("generated/stringOwners"))
}

kotlin {
    compilerOptions { jvmTarget.set(Versions.jvmTarget) }
    sourceSets.main {
        kotlin.srcDir(generateStringOwners)
        kotlin.srcDir(generateBuildInfo)
        resources.srcDir(copyAppIcon.map { it.destinationDir.parentFile })
    }
}

java {
    sourceCompatibility = Versions.javaVersion
    targetCompatibility = Versions.javaVersion
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.material3)

    // The shared app: :appshell exposes every client module as `api`, so this one line brings the
    // whole graph the desktop build needs.
    implementation(project(":appshell"))
    // The database is opened here, so its builder has to be visible - :appshell does not export it.
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    // The shared implementations this shell binds - the logger, RxBus, DateUtil and L.
    implementation(project(":implementation"))
    implementation(project(":shared:clientbindings"))
    implementation(project(":shared:impl"))

    // The Main dispatcher. Compose Desktop draws on the Swing event thread, and shared code hops to
    // Dispatchers.Main to touch the UI; core alone has no Main dispatcher, so this is what stops
    // every such hop throwing at runtime.
    implementation(libs.kotlinx.coroutines.swing)

    // The JSON implementation under the Nightscout websocket.
    //
    // socket.io-client brings org.json:json (Crockford's) transitively. On Android that jar is
    // inert because the platform provides org.json; on a desktop JVM it would be the real thing,
    // and the two disagree on real cases - optString of a JSON null gives "" in Crockford's and
    // "null" on Android. That difference would sit under the wire path that becomes treatments.
    //
    // So the desktop runs AOSP's org.json, repackaged for the JVM: the same implementation the
    // phone runs, so a payload parses identically on both.
    implementation(libs.org.json.android)

    // The plugins the client runs, mirroring the list :ios:shell carries. Metro finds their
    // @ContributesBinding classes through these, which is what turns the graph from an anchor into
    // the real app. No pump driver except the virtual one: a desktop has no Bluetooth radio to
    // reach a pump with, and this build is a follower.
    implementation(project(":core:data"))
    implementation(project(":core:graph"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":plugins:aps"))
    implementation(project(":plugins:automation"))
    implementation(project(":plugins:calibration"))
    implementation(project(":plugins:configuration"))
    implementation(project(":plugins:constraints"))
    implementation(project(":plugins:main"))
    implementation(project(":plugins:sensitivity"))
    implementation(project(":plugins:smoothing"))
    implementation(project(":plugins:source"))
    implementation(project(":plugins:sync"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))
    implementation(project(":ui"))

    // The string owner list is hand written, so it needs a test that a wrong owner name fails.
    testImplementation(kotlin("test"))
}

tasks.withType<Test> { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "app.aaps.desktop.shell.MainKt"

        nativeDistributions {
            // The client name, matching `DesktopClientConfig.appName` and the `aapsclient` flavour
            // on Android. Not just cosmetic: Windows takes the notification header from the
            // launcher this produces, so a package called "AAPS" would put the master name on a
            // follower every time a notification appeared.
            packageName = "AAPSClient"
            // jpackage demands a plain numeric version - an MSI rejects anything else - so the
            // `-dev-b-kmp` style suffix that Versions.appVersion carries is trimmed off here.
            packageVersion = Versions.appVersion.substringBefore('-')
            // jpackage embeds a JRE, so the user installs an app rather than a JVM.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
        }
    }
}

// Crockford's org.json comes in transitively with socket.io-client. It is excluded so that the
// AOSP repack above is the only implementation on the classpath - two would make which one wins a
// matter of ordering.
configurations.configureEach {
    exclude(group = "org.json", module = "json")
}
