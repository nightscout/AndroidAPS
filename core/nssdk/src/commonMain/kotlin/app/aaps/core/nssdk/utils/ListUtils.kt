package app.aaps.core.nssdk.utils

// @JvmSynthetic was here to hide this helper from Java callers. It is a JVM-only annotation, and the
// function is `internal` anyway, so it is dropped rather than made platform specific.
internal fun <E> List<E?>?.toNotNull(): List<E> = this?.filterNotNull() ?: listOf()
