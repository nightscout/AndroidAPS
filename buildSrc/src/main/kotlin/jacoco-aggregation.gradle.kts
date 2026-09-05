import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("jacoco")
}

/**
 * The name of the debug variant this module builds.
 *
 * Only the app modules have product flavours; a library is unflavoured, so its output sits under
 * `debug` and not `fullDebug`. Ask the module rather than assuming one name for all: getting this
 * wrong makes the module contribute no classes and no execution data, which shows up as a silent
 * coverage drop instead of an error. Reflection because the root project has no typed accessor for
 * another module's `android` extension.
 */
fun Project.debugVariantName(): String {
    val flavored = runCatching {
        val android = extensions.findByName("android") ?: return@runCatching false
        val flavors = android.javaClass.getMethod("getProductFlavors").invoke(android) as? Collection<*>
        flavors?.isNotEmpty() == true
    }.getOrDefault(false)
    return if (flavored) "fullDebug" else "debug"
}

project.afterEvaluate {

    tasks.register<JacocoReport>(name = "jacocoAllDebugReport") {

        group = "Reporting"
        description = "Generate overall Jacoco coverage report for the debug build."

        reports {
            html.required.set(true)
            xml.required.set(true)
        }

        val excludes = listOf(
            "**/di/*Module.class",
            "**/di/*Component.class",
            "**/com/jjoe64/**/*.*",
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "androidx/**/*.*",
            "**/*\$ViewInjector*.*",
            "**/*MembersInjector*.*",
            "**/*_Factory.*",
            "**/*_Provide*Factory*.*",
            "**/*_ViewBinding*.*",
            "**/AutoValue_*.*",
            "**/R2.class",
            "**/R2$*.class",
            "**/*Directions$*",
            "**/*Directions.*",
            "**/*Binding.*",
            "**/BR.class",
            // Compose @Preview-only files: exclude the whole class including the synthetic $lambda$N
            // methods the Compose compiler extracts from each preview lambda (a method-level
            // annotation cannot reach those extracted methods). Convention: keep every @Preview
            // function in a dedicated *Previews.kt file.
            "**/*PreviewsKt.class",
            "**/*PreviewsKt$*.class"
        )

        val classes = HashSet<ConfigurableFileTree>()
        subprojects.forEach { proj ->
            // A multiplatform module has no build variants, so the variant paths below cannot
            // legitimately hold anything for it. Deciding up front matters, and not only for
            // tidiness: a module converted from an Android library leaves its old
            // intermediates/built_in_kotlinc/fullDebug behind in an existing build directory, and
            // feeding both that and the new output to JaCoCo fails the whole report with
            // "Can't add different class with same name".
            //
            // Ask the plugin, not the directory layout. `:pump:combov2:comboctl` is a plain Android
            // library that keeps its upstream comboctl source layout - `src/commonMain` and
            // `src/androidMain`, wired by hand in its build file - so a directory check called it
            // multiplatform and sent it to `classes/kotlin/android/main`, which it does not have. It
            // then contributed no classes and no execution data, dropping about 8000 well covered
            // lines from the report with nothing failing.
            if (proj.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                // Take the Android compilation when the module has one, the JVM compilation
                // otherwise. Never both: a multiplatform module compiles the same commonMain code
                // once per target, and only the target whose tests actually ran produces .exec
                // data. Counting the other copy would report identical code as 0% covered and make
                // the number worse than leaving the module out.
                val kmpTarget = if (File("${proj.projectDir}/src/androidMain").isDirectory) "android" else "jvm"
                val kmpPath = proj.layout.buildDirectory.dir("classes/kotlin/$kmpTarget/main").get()
                classes.add(fileTree(kmpPath) { exclude(excludes); include("**/*.class") })
            } else {
                val variant = proj.debugVariantName()
                // Use variant directory as base - fileTree recurses into subdirectories
                // This avoids hardcoding exact task-name subdirectories that may vary between AGP versions
                val javaPath = proj.layout.buildDirectory.dir("intermediates/javac/$variant").get()
                classes.add(fileTree(javaPath) { exclude(excludes); include("**/*.class") })
                val kotlinPath = proj.layout.buildDirectory.dir("intermediates/built_in_kotlinc/$variant").get()
                classes.add(fileTree(kotlinPath) { exclude(excludes); include("**/*.class") })
                // Fallback for older AGP versions
                val kotlinLegacyPath = proj.layout.buildDirectory.dir("tmp/kotlin-classes/$variant").get()
                classes.add(fileTree(kotlinLegacyPath) { exclude(excludes); include("**/*.class") })
            }
        }
        classDirectories.setFrom(files(listOf(classes)))

        val sources = mutableListOf<String>().also {
            subprojects.forEach { proj ->
                val variant = proj.debugVariantName()
                it.add("${proj.projectDir}/src/main/java")
                it.add("${proj.projectDir}/src/main/kotlin")
                it.add("${proj.projectDir}/src/$variant/java")
                it.add("${proj.projectDir}/src/$variant/kotlin")
                // Multiplatform source sets. A missing directory contributes nothing, so these are
                // harmless on the modules that are still plain Android.
                it.add("${proj.projectDir}/src/commonMain/kotlin")
                it.add("${proj.projectDir}/src/androidMain/kotlin")
                it.add("${proj.projectDir}/src/jvmMain/kotlin")
            }
        }
        sourceDirectories.setFrom(files(sources))

        val executions = mutableListOf<File>()
        subprojects.forEach { proj ->
            val variant = proj.debugVariantName()
            val path = proj.layout.buildDirectory.dir("outputs/unit_test_code_coverage/${variant}UnitTest/test${variant.replaceFirstChar(Char::titlecase)}UnitTest.exec").get()
            files(path).forEach { file ->
                println("Collecting execution data from: ${file.absolutePath}")
                executions.add(file)
            }
            val androidPath = proj.layout.buildDirectory.dir("outputs/code_coverage/${variant}AndroidTest/connected/").get()
            fileTree(androidPath).forEach { file ->
                println("Collecting android execution data from: ${file.absolutePath}")
                executions.add(file)
            }
            // Multiplatform test tasks write here instead - testAndroidHostTest.exec for a module
            // with an Android target, jvmTest.exec otherwise. Matched to the class directories
            // chosen above.
            val kmpJacocoPath = proj.layout.buildDirectory.dir("jacoco").get()
            fileTree(kmpJacocoPath) { include("*.exec") }.forEach { file ->
                println("Collecting multiplatform execution data from: ${file.absolutePath}")
                executions.add(file)
            }
        }
        executionData.setFrom(executions)
    }
}
