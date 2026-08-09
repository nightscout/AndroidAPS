package app.aaps.core.data.model

/** Plain `kotlin.assert`: active under `-ea` (so in tests), a no-op in production. */
internal actual fun devAssert(value: Boolean) {
    assert(value)
}
