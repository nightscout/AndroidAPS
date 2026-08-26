package app.aaps.di.metro

import dev.zacsweers.metro.BindingContainer

/**
 * The follower build's copy of the pump container: empty, on purpose.
 *
 * A follower has no pump module on its classpath, so nothing here is needed and nothing can be
 * provided - `BleTransport` does not exist in this build. `AppRootGraph` still includes the container,
 * because its factory is declared in `src/main` and has to compile for every flavour; this copy is what
 * makes that possible.
 *
 * See the `src/withPumps` copy for the version with bindings, and for why they cannot live in
 * `AapsLeaves` instead.
 */
@BindingContainer
class PumpLeaves
