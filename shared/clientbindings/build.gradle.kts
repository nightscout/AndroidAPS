plugins {
    kotlin("multiplatform")
    alias(libs.plugins.metro)
}

/**
 * The graph wiring the two non-Android shells share.
 *
 * `:app`, `:ios:shell` and `:desktop:shell` each have to state the same handful of bindings, because
 * the classes behind them carry no DI annotations: `IobCobCalculatorPlugin` is built twice on purpose,
 * `NSClientV3Plugin` breaks the annotation processor, `ObjectivesPlugin` carries `@APS`. Android says
 * them in `:app`; iOS and desktop each said them separately, and had begun to drift - the desktop
 * shell was missing the Nightscout plugin entirely, which showed up as "no sync plugins".
 *
 * This module holds the ones whose construction is identical off Android, so there is one copy.
 *
 * ## Why a module rather than `:appshell`
 *
 * The bindings name plugin classes - `:plugins:main`, `:plugins:constraints`, `:plugins:sync`,
 * `:workflow`. `:appshell` depends on none of those, and `:app` builds against `:appshell`, so adding
 * them there would push the plugin modules into the Android app's own path. A separate module keeps
 * that dependency where it belongs: on the two shells that want it.
 *
 * ## Why it is not contributed
 *
 * `ClientGraphBindings` is a plain `@BindingContainer`, so a graph has to include it by name. That is
 * deliberate: `@ContributesTo(AppScope::class)` would put it in **every** graph including Android's,
 * where `:app` already provides the same bindings and the two would collide. Same reasoning as
 * `CoreObjectsGraph`.
 */
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":implementation"))
                implementation(project(":shared:impl"))
                implementation(project(":plugins:constraints"))
                implementation(project(":plugins:main"))
                implementation(project(":plugins:sync"))
                implementation(project(":workflow"))

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
