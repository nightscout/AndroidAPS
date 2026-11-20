import java.util.zip.ZipFile

plugins {
    id("kmp-android-library")
    id("kotlinx-serialization")
}

kotlin {
    android {
        namespace = "app.aaps.plugins.eversense"
        withHostTest { isIncludeAndroidResources = true; isReturnDefaultValues = true }
    }
    sourceSets {
        androidMain.dependencies {
            api(libs.androidx.core)
            api(project.dependencies.platform(libs.kotlinx.serialization.bom))
            api(libs.kotlinx.serialization.json)
            api(libs.org.slf4j.api)
            api(libs.com.github.tony19.logback.android)
            implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
            implementation("org.bouncycastle:bcprov-jdk18on:1.81")
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.org.junit.jupiter)
            implementation(libs.org.junit.jupiter.api)
            runtimeOnly(libs.org.junit.platform.launcher)
            implementation(libs.org.mockito.kotlin)
            implementation(libs.org.mockito.junit.jupiter)
            implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
