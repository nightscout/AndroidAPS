package app.aaps.core.data.model

/**
 * A developer check that behaves like `assert` on every target.
 *
 * `kotlin.assert` is **not in the common standard library**. The JVM has it, and Kotlin/Native has
 * its own behind `@ExperimentalNativeApi`, but there is no common declaration - so `assert(...)` in
 * `commonMain` type-checks in each target's own compilation and then fails the *metadata*
 * compilation with "Unresolved reference 'assert'". That failure only appears once some other module
 * consumes this one from its own `commonMain`, which is exactly when it matters.
 *
 * Kept as an assertion rather than turned into `require()`: JVM assertions are off in production and
 * on under `-ea` in tests, while `require()` always throws. Swapping them would change behaviour in
 * shipped code.
 */
internal expect fun devAssert(value: Boolean)
