package app.aaps.shared.tests

import app.aaps.core.interfaces.InterfacesStringsValues
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.KeysStringsValues
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStringsValues
import app.aaps.implementation.ImplementationStringsValues
import app.aaps.implementation.resources.GeneratedTextResolver
import app.aaps.plugins.aps.ApsStringsValues

/**
 * A [TextResolver] that answers with the REAL English text, so a test need not stub `rh.gs` at all.
 *
 * ## Why this exists
 *
 * A test using the mocked `rh` has to stub every string it touches, because Mockito answers an
 * unstubbed `gs` with null and the return type is not nullable. That is 624 `whenever(rh.gs(...))`
 * lines across 148 files, and almost all of them exist only to avoid the null - the text they return
 * is invented and nobody asserts it.
 *
 * Nothing new had to be built for this. `GenerateKeyStringsTask` already emits the English text of
 * every string as `<Module>StringsValues`, because iOS and the desktop JVM have no Android resource
 * table and need it. [GeneratedTextResolver] already reads those maps. This just points the two at
 * each other from a test.
 *
 * ## What it resolves, and what it cannot
 *
 * Only [app.aaps.core.keys.interfaces.TextRef.Named] - the generated, platform neutral form. A
 * [app.aaps.core.keys.interfaces.TextRef.AndroidRes] holds an AAPT id that means nothing off Android,
 * so it renders as `res:<id>`, and the legacy `gs(Int)` overload does not exist on [TextResolver] at
 * all. A test whose subject still uses resource ids has to keep the mock.
 *
 * Only the five owners `:shared:tests` can see. Adding more would mean taking a module dependency
 * just to resolve a string, which is exactly what the house rule forbids; a test in one of those
 * modules can call [TextRefValueRegistry.register] itself with its own generated object.
 *
 * English only. The generated maps carry translations too, but a test that depended on a locale
 * would be a test that fails when a translator edits Crowdin.
 *
 * ## Isolation
 *
 * [TextRefValueRegistry] is process-wide mutable state, so this clears it before registering. Call it
 * per test (or from `@BeforeEach`), never once for a whole run, or the owners one test registered
 * leak into the next.
 */
fun generatedTextResolver(): TextResolver {
    TextRefValueRegistry.clear()
    TextRefValueRegistry.register("keys", KeysStringsValues::textOf)
    TextRefValueRegistry.register("coreUi", CoreUiStringsValues::textOf)
    TextRefValueRegistry.register("interfaces", InterfacesStringsValues::textOf)
    TextRefValueRegistry.register("implementation", ImplementationStringsValues::textOf)
    TextRefValueRegistry.register("aps", ApsStringsValues::textOf)
    return GeneratedTextResolver()
}

/**
 * The same, but anything the generated maps cannot answer falls through to [fallback].
 *
 * For a test whose subject is half migrated - some strings taken from a generated object, the rest
 * still `R.string` ids. The [TextRef.Named] ones resolve for real and need no stub; the
 * [TextRef.AndroidRes] ones reach the mock, so the handful of `whenever(rh.gs(R.string.x))` lines that
 * genuinely matter can stay while the rest go.
 *
 * Pass the test's mocked `rh`. Without a fallback an unresolvable id renders as `res:<id>`, which is
 * honest but fails any assert on the text.
 */
fun generatedTextResolver(fallback: TextResolver): TextResolver {
    val generated = generatedTextResolver()
    return object : TextResolver {
        override fun gs(ref: TextRef): String =
            if (ref is TextRef.AndroidRes) fallback.gs(ref) else generated.gs(ref)

        override fun gs(ref: TextRef, vararg args: Any?): String =
            if (ref is TextRef.AndroidRes) fallback.gs(ref, *args) else generated.gs(ref, *args)

        override fun gsNotLocalised(ref: TextRef): String =
            if (ref is TextRef.AndroidRes) fallback.gsNotLocalised(ref) else generated.gsNotLocalised(ref)

        override fun shortTextMode(): Boolean = generated.shortTextMode()
    }
}
