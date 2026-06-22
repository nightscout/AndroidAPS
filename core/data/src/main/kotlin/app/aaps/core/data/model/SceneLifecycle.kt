package app.aaps.core.data.model

/**
 * Lifecycle phase of an active scene, owned by the master and synced to clients via
 * the runningConfig snapshot. Domain-level type; the wire DTO carries it as a String
 * to keep core/nssdk independent of core/data.
 */
enum class SceneLifecycle {

    /** Scene is running, duration has not yet elapsed (or scene is indefinite). */
    ACTIVE,

    /**
     * Scene's duration has elapsed and the master has run its expiry side-effects
     * (revert SMB, schedule chain, etc.). Banner shows the "Close" affordance.
     */
    EXPIRED
}
