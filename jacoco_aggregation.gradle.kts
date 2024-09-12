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

        val classes = HashSet<ConfigurableFileTree>()
        subprojects.forEach { proj ->
            variants.forEach { variant ->
                val path1 = proj.layout.buildDirectory.dir("intermediates/javac/$variant/classes").get()
                classes.add(fileTree(path1) { exclude(excludes) })
                val path2 = proj.layout.buildDirectory.dir("tmp/kotlin-classes/$variant").get()
                classes.add(fileTree(path2) { exclude(excludes) })
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

        val executions = mutableListOf<File>()
        subprojects.forEach { proj ->
            variants.forEach { variant ->
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
            }
        }
        executionData.setFrom(executions)
    }
}
