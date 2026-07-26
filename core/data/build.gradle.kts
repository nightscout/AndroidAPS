plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = Versions.javaVersion
    targetCompatibility = Versions.javaVersion
}

dependencies {
    testImplementation(libs.org.junit.jupiter)
    testImplementation(libs.com.google.truth)
    testRuntimeOnly(libs.org.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}