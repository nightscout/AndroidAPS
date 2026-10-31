package app.aaps.di

import app.aaps.core.interfaces.configuration.ExternalOptions

/**
 * Which [ExternalOptions] an instrumented test wants reported as enabled.
 * In production these are toggled by dropping a marker file into the AAPS "extra" directory, which
 * `ConfigImpl.isEnabled` looks up via `FileListProvider.ensureExtraDirExists()`. That path is
 * unreachable in-process: it resolves a SAF *tree* URI (`DocumentFile.fromTreeUri`) taken from the
 * `AapsDirectoryUri` preference, and the instrumented tests deliberately never grant an AAPS
 * directory (there is no way to grant a SAF tree without driving the system picker). So the flags
 * always read false and no pump emulator could ever be selected.
 */
object EmulatedOptions {

    @Volatile var enabled: Set<ExternalOptions> = emptySet()
}
