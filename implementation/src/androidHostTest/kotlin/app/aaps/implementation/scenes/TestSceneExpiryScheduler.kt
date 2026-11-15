package app.aaps.implementation.scenes

/**
 * A [SceneExpiryScheduler] that records what the executor asked for.
 *
 * Scheduling used to be `WorkManager.getInstance(context)` inside [SceneExecutor], wrapped in a
 * `try/catch` that logged and swallowed. In a unit test that call always threw on the mocked context,
 * so every test silently exercised the "scheduling failed" path and nothing could assert that a timed
 * scene had actually armed its expiry - the one thing that takes the scene's temp target and profile
 * switch off again.
 */
class TestSceneExpiryScheduler : SceneExpiryScheduler {

    /** Every [schedule] call, in order, as scene name to delay. */
    val scheduled = mutableListOf<Pair<String, Long>>()
    var cancelCount: Int = 0
        private set

    override fun schedule(sceneName: String, delayMs: Long) {
        scheduled += sceneName to delayMs
    }

    override fun cancel() {
        cancelCount++
    }
}
