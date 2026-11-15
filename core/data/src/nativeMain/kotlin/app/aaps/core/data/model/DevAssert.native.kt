@file:OptIn(ExperimentalNativeApi::class)

package app.aaps.core.data.model

import kotlin.experimental.ExperimentalNativeApi

/**
 * Kotlin/Native has `assert` too, behind an opt-in. Scoping the opt-in to this one file is better
 * than a Gradle-wide `optIn`, which would silently allow experimental Native APIs anywhere in the
 * module - and which does not reach the metadata compilation anyway.
 */
actual fun devAssert(value: Boolean) {
    assert(value)
}
