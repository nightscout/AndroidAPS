package app.aaps.shared.tests

import app.aaps.core.interfaces.InterfacesStringIds
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.KeysStringIds
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.implementation.ImplementationStringIds
import app.aaps.plugins.aps.ApsStringIds
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

/**
 * Teaches a mocked [ResourceHelper] to answer the [TextRef] forms of `gs`.
 *
 * `ResourceHelper` has real bodies for these, but a Mockito mock does not run them, so an unstubbed
 * call returns null. That matters as soon as production code stops passing resource ids: a test that
 * stubs `whenever(rh.gs(R.string.mgdl))` keeps compiling and silently starts getting null - or, if
 * the name were used as a fallback, the wrong string ("mgdl" instead of "mg/dl").
 *
 * Both forms are routed back to the `gs(Int)` stubs the tests already write, so nothing about how a
 * test sets up its strings has to change.
 *
 * [TestBaseWithProfile] calls this for its own `rh`. A test that mocks `ResourceHelper` itself
 * should call it too, right after the mock exists.
 */
fun stubTextRefResolution(rh: ResourceHelper) {
    doAnswer { invocation: InvocationOnMock ->
        resolve(rh, invocation.getArgument(0))
    }.whenever(rh).gs(any<TextRef>())

    doAnswer { invocation: InvocationOnMock ->
        val args = invocation.arguments.drop(1).toTypedArray()
        // Ask through the mock rather than calling resolve() directly, so a test that stubbed this
        // exact ref gets its own text. Only when it did not does this fall through to the generic
        // answer above. Without that, a module whose owner is not in the map below would format the
        // raw name instead of the stubbed template.
        // Through a local of nullable type on purpose: `gs` is declared non-null, so an elvis on the
        // call itself is compiled away and a mock that returns null reaches String.format, which
        // then throws instead of showing what the test actually stubbed.
        val template: String? = rh.gs(invocation.getArgument<TextRef>(0))
        String.format(template ?: "", *args)
    }.whenever(rh).gs(any<TextRef>(), anyVararg())
}

private fun resolve(rh: ResourceHelper, ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.AndroidRes ->
        if (ref.args.isEmpty()) rh.gs(ref.id)
        else rh.gs(ref.id, *ref.args.toTypedArray())

    is TextRef.Named      -> namedIdOf(ref)
        ?.let { if (ref.args.isEmpty()) rh.gs(it) else rh.gs(it, *ref.args.toTypedArray()) }
        ?: error(
            "Unresolved string '${ref.name}' (owner '${ref.owner}'): either this module's id map is not one of the five " +
                "known here, or the id is known but the mock has no stub for it. This used to answer with the NAME, so a " +
                "test could assert \"${ref.name}\" and pass forever while the app showed something else. Use " +
                "generatedTextResolver(\"${ref.owner}\" to ${ref.owner.replaceFirstChar { it.uppercase() }}StringsValues::textOf) " +
                "for the real English text, or stub this ref on the mock."
        )
}

/**
 * The owners a test can resolve by name.
 *
 * `ResourceHelper` dispatches on the owner and asks the generated id map of the module that declared
 * the string. These are the five whose maps `:shared:tests` already depends on, so the mock does
 * exactly what production does.
 *
 * The other eleven owners - `main`, `automation`, `sync`, `constraints`, `calibration`, `sensitivity`,
 * `smoothing`, `source`, `virtual`, `configuration`, `ui` - live in modules this one does not depend
 * on, and must NOT be added by taking a new dependency just to make a test string resolve.
 *
 * **They throw rather than falling back to the raw name.** Returning the name is what the real
 * resolver does on a device when nobody registered the owner, but in a test it is poison: the string
 * silently becomes its own snake_case name, and an assertion written against that name passes forever
 * while the app shows something else. It hid four wrong assertions - `alreadyset`, `locationis`,
 * `wifissidcompared` and an objective reason - until the migration to real text flushed them out.
 *
 * A test in one of those modules has two honest options: `generatedTextResolver("<owner>" to
 * <Owner>StringsValues::textOf)` for the real English, or stubbing the ref it cares about on the mock.
 */
private fun namedIdOf(ref: TextRef.Named): Int? = when (ref.owner) {
    "keys"           -> KeysStringIds.idOf(ref.name)
    "coreUi"         -> CoreUiStringIds.idOf(ref.name)
    "interfaces"     -> InterfacesStringIds.idOf(ref.name)
    "implementation" -> ImplementationStringIds.idOf(ref.name)
    "aps"            -> ApsStringIds.idOf(ref.name)
    else             -> null
}
