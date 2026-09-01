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
 * and shows [app.aaps.appshell.AapsAppRoot].
 *
 * A plain `kotlin("jvm")` module rather than a multiplatform one: it has exactly one target, and
 * Gradle resolves the `jvm` variant of every multiplatform module it depends on.
 */
kotlin {
    compilerOptions { jvmTarget.set(Versions.jvmTarget) }
}

java {
    sourceCompatibility = Versions.javaVersion
    targetCompatibility = Versions.javaVersion
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // The shared app: :appshell exposes every client module as `api`, so this one line brings the
    // whole graph the desktop build needs.
    implementation(project(":appshell"))
    // The database is opened here, so its builder has to be visible - :appshell does not export it.
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    // The shared implementations this shell binds - the logger, RxBus, DateUtil and L.
    implementation(project(":implementation"))
    implementation(project(":shared:impl"))

    // The Main dispatcher. Compose Desktop draws on the Swing event thread, and shared code hops to
    // Dispatchers.Main to touch the UI; core alone has no Main dispatcher, so this is what stops
    // every such hop throwing at runtime.
    implementation(libs.kotlinx.coroutines.swing)

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
            packageName = "AAPS"
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
