package app.aaps.core.interfaces.scenes

/**
 * Domain owner of scene definitions. The whole scene list syncs as a plain blob over the generic key-sync
 * path (StringNonKey.SceneDefinitions is SyncSpec(Cold, Bidirectional)) — blob-level last-writer-wins, like
 * QuickWizard/TT presets. (No per-scene merge: a stale client save can drop a master scene it hasn't yet
 * received — accepted because scene editing is effectively a one-time setup.)
 */
interface Scenes
