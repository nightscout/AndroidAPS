package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.autotune.AutotuneFileStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import java.io.File

/**
 * The Autotune folders under `~/AAPS/autotune`.
 *
 * A phone keeps them in its log folder, which the user reaches over USB. The desktop's log folder is
 * the hidden data folder, so they go under the visible root instead, next to the settings exports:
 * they are files the user copies to a computer running oref0.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AutotuneFileStore>())
@Inject
class DesktopAutotuneFileStore(
    /** Overridden in a test so it neither writes into the user's folder nor leaves files behind. */
    private val folder: File = DesktopFolders.autotune
) : JvmAutotuneFileStore() {

    override val directory: String get() = folder.path
}
