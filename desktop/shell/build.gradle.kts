plugins {
    kotlin("jvm")
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
}

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
