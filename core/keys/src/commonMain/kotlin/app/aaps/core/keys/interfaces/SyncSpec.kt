package app.aaps.core.keys.interfaces

/**
 * Which running-config document a synced preference rides in:
 * - [Cold]: the rarely-changing config doc (plugin config, settings).
 * - [Hot]: the small, frequently-republished runtime doc (e.g. active scene).
 */
enum class SyncChannel {

    Cold, Hot
}

/**
 * Sync authority for a preference key:
 * - [MasterOnly]: master → client only (the client may read/display but never push back). Status quo.
 * - [Bidirectional]: the client may also edit it; the edit is pushed to the master, which applies
 *   last-writer-wins by the per-key modified stamp and republishes so devices converge.
 */
enum class SyncDirection {

    MasterOnly, Bidirectional
}

/**
 * Single source of truth for how a preference key participates in device-to-device sync. Declared
 * on the key enum entry itself (see [NonPreferenceKey.sync]); `null` means the key is not synced.
 *
 * Structured documents (insulin, scenes, automation, quick wizard JSON) use it too. The sync layer
 * only writes the value. A class that parses one into memory must observe its key and reload itself,
 * on the master as well as on the client: a client edit reaches the master through the same write,
 * and nothing else tells the class that the value changed.
 */
data class SyncSpec(
    val channel: SyncChannel,
    val direction: SyncDirection
)
