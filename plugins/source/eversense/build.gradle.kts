import java.util.zip.ZipFile

plugins {
    id("kmp-android-library")
    id("kotlinx-serialization")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

// The pairing wizard and the other shared screens are in commonMain and use the generated EversenseStrings
registerKeyStrings("eversense")

// The whole Eversense CGM in one module, as a pump driver has one: the transmitter driver and the AAPS plugin with its
// screens. :plugins:source does not depend on it; it depends on :plugins:source for the shared BG source base.
kotlin {
    android {
        namespace = "app.aaps.plugins.eversense"
        androidResources { enable = true }
        withHostTest { isIncludeAndroidResources = true; isReturnDefaultValues = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":core:interfaces"))
            implementation(project(":core:keys"))
            implementation(project(":core:objects"))
            implementation(project(":core:ui"))
            implementation(project(":core:utils"))
            implementation(project(":plugins:source"))

            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.material3)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            api(libs.androidx.core)
            api(project.dependencies.platform(libs.kotlinx.serialization.bom))
            api(libs.kotlinx.serialization.json)
            api(libs.org.slf4j.api)
            api(libs.com.github.tony19.logback.android)
            implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
            implementation("org.bouncycastle:bcprov-jdk18on:1.81")
            implementation(libs.androidx.appcompat)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.runtime)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        // Hand written: the test convention plugins apply com.android.library, which a multiplatform module cannot.
        getByName("androidHostTest").dependencies {
            implementation(project(":shared:tests"))
            implementation(kotlin("test"))
            implementation(libs.org.junit.jupiter)
            implementation(libs.org.junit.jupiter.api)
            implementation(libs.org.mockito.kotlin)
            implementation(libs.org.mockito.junit.jupiter)
            implementation(libs.com.google.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.org.robolectric)
            // The real org.json: the platform stub answers null under isReturnDefaultValues
            implementation(libs.org.json.android)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.com.squareup.okhttp3.mockwebserver)
            runtimeOnly(libs.org.junit.vintage.engine)
            runtimeOnly(libs.org.junit.platform.launcher)
        }
    }
}

// Compile the frozen implementation in an isolated package for byte/result comparison tests.
val generateProtocolReference by tasks.registering {
    val archive = layout.projectDirectory.file("src/androidHostTest/resources/protocol-reference.zip")
    val destination = layout.buildDirectory.dir("generated/protocolReference")
    inputs.file(archive)
    outputs.dir(destination)
    doLast {
        val output = destination.get().asFile
        ZipFile(archive.asFile).use { zip ->
            zip.entries().asSequence().filter { it.name.endsWith(".kt") && !it.name.endsWith("EversenseLogger.kt") }.forEach { entry ->
                val target = output.resolve(entry.name)
                check(target.toPath().normalize().startsWith(output.toPath().normalize()))
                target.parentFile.mkdirs()
                val source = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    .replace("app.aaps.plugins.eversense", "app.aaps.plugins.eversense.reference")
                    .replace("app.aaps.plugins.eversense.reference.util.EversenseLogger", "app.aaps.plugins.eversense.util.EversenseLogger")
                    .replace(Regex("private const val (eu(?:Token|Upload|Care|Vault)BaseUrl)"), "internal var $1")
                    .replace("System.currentTimeMillis()", "app.aaps.plugins.eversense.protocol.PacketTime.nowMillis()")
                    .replace("SystemClock.elapsedRealtime()", "app.aaps.plugins.eversense.protocol.PacketTime.nowMillis()")
                val prepared = if ("EversenseLogger" in source && "import app.aaps.plugins.eversense.util.EversenseLogger" !in source) {
                    val end = source.indexOf('\n') + 1
                    source.substring(0, end) + "\nimport app.aaps.plugins.eversense.util.EversenseLogger\n" + source.substring(end)
                } else source
                target.writeText(prepared, Charsets.UTF_8)
            }
        }
    }
}
kotlin.sourceSets.getByName("androidHostTest").kotlin.srcDir(generateProtocolReference)
