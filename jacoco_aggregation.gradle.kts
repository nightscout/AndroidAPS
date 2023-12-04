apply(plugin = "jacoco")

project.afterEvaluate {
    val variants = listOf("fullDebug")

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
            "**/*Dagger*.*",
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
            "**/BR.class"
        )

        val classesDirectories = mutableListOf<String>().also {
            subprojects.forEach { proj ->
                variants.forEach { variant ->
                    it.add("${proj.buildDir}/intermediates/javac/$variant/classes")
                    it.add("${proj.buildDir}/tmp/kotlin-classes/$variant")
                }
            }
        }

        val classes = HashSet<ConfigurableFileTree>().also {
            classesDirectories.forEach { path ->
                it.add(fileTree(path) { exclude(excludes) })
            }
        }

        classDirectories.setFrom(files(listOf(classes)))

        val sources = mutableListOf<String>().also {
            subprojects.forEach { proj ->
                variants.forEach { variant ->
                    it.add("${proj.projectDir}/src/main/java")
                    it.add("${proj.projectDir}/src/main/kotlin")
                    it.add("${proj.projectDir}/src/$variant/java")
                    it.add("${proj.projectDir}/src/$variant/kotlin")
                }
            }
        }
        sourceDirectories.setFrom(files(sources))

        val executions = mutableListOf<String>().also {
            subprojects.forEach { proj ->
                variants.forEach { variant ->
                    val path = "${proj.buildDir}/outputs/unit_test_code_coverage/${variant}UnitTest/test${variant.replaceFirstChar(Char::titlecase)}UnitTest.exec"
                    if ((File(path)).exists()) {
                        it.add(path)
                        println("Collecting execution data from: $path")
                    }
                    val androidPath = "${proj.buildDir}/outputs/code_coverage/${variant}AndroidTest/connected/"
                    val androidFiles = fileTree(androidPath)
                    androidFiles.forEach { file ->
                        it.add(file.path)
                        println("Collecting android execution data from: ${file.path}")
                    }
                }
            }
        }

        executionData.setFrom(files(executions))
    }
}
