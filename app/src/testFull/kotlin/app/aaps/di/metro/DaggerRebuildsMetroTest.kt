package app.aaps.di.metro

import app.aaps.shared.tests.metroOwnedRebuiltByDagger
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * The mirror of [SplitBrainTest]: nothing Metro owns is also built by Dagger.
 *
 * `SplitBrainTest` asks whether a javax `@Singleton` is rebuilt by Metro. This asks the opposite, and
 * it exists because that gap cost a real defect. When the pump drivers moved to `@SingleIn`,
 * `DanaModules.provideRfcommTransport` kept taking `DanaRPlugin`, `DanaRKoreanPlugin` and
 * `DanaRv2Plugin` as parameters. Dagger has no idea what `@SingleIn` means - it saw an `@Inject`
 * constructor and built its own copies. That provider calls `setPluginEnabled` on them, so with a Dana
 * emulator option enabled the auto-switch flipped objects that were not in the plugin list. It
 * compiled, it ran, and every existing guard passed.
 *
 * If this fails, do **not** silence it by scoping the Dagger side as well - that gives two correctly
 * scoped objects, which is the same bug. Take `MetroGraphs` in the provider and ask it for the
 * instance, the way the `CoreObjectsModule` delegates do, or move the provider to Metro.
 */
class DaggerRebuildsMetroTest {

    @Test
    fun `no Dagger provider builds something Metro owns`() {
        val reports = metroOwnedRebuiltByDagger(
            anchors = listOf(AapsLeaves::class.java),
            // MetroGraphs is the sanctioned way across: a provider that takes it is reading Metro's
            // instance, not building a second one.
            graphTypes = setOf(MetroGraphs::class.java)
        )

        assertWithMessage(
            "These carry Metro's @SingleIn, but a Dagger @Provides/@Binds takes them as a parameter, so " +
                "Dagger builds its own. Take MetroGraphs and read the instance instead."
        ).that(reports).isEmpty()
    }
}
