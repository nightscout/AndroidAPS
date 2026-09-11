package app.aaps.core.interfaces.scenes

import app.aaps.core.data.model.Scene

/**
 * Resolves the "can we chain to a follow-up scene right now?" policy for the End-Scene dialog. Two
 * variants because a client can't see the master's runtime state:
 *  - [resolveRunnableChainTarget] (master) — full check incl. loop/pump/profile preconditions.
 *  - [resolveCatalogChainTarget] (client) — catalog-only; the master re-checks at receipt.
 *
 * Exposed through `core:interfaces` so the VMs can drive the dialog without depending on the
 * `:implementation` resolver.
 */
interface SceneChainResolver {

    /** Master-side: chain target only if configured, enabled, AND runtime currently allows activation. */
    suspend fun resolveRunnableChainTarget(activeScene: Scene): Scene?

    /** Client-side: chain target if configured + enabled (ignores local runtime — master re-validates). */
    fun resolveCatalogChainTarget(activeScene: Scene): Scene?
}
