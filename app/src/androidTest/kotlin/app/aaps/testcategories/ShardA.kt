package app.aaps.testcategories

/**
 * Marks an instrumented test class as **shard A** so CI can split the app module's androidTest suite
 * across two emulators. The runner filters natively on this:
 *  - **shard A** runs `-e annotation app.aaps.testcategories.ShardA`
 *  - **shard B** runs `-e notAnnotation app.aaps.testcategories.ShardA` — i.e. *everything else*,
 *    including any new, untagged test. Using the complement (not a `ShardB` annotation) means a test
 *    can never fall into neither shard and be silently skipped.
 *
 * Balance is by measured time, not test count. The two heavy Dana UI tests dominate, so they are
 * split one per emulator:
 *  - **A** ≈ the DanaRS suite incl. `DanaRsEmulatorUiTest` (the heavy RS UI walk) + `DanaREmulatorPumpTest`
 *  - **B** ≈ `DanaREmulatorUiTest` (the DanaR UI delivery, deliberately NOT `@ShardA`) + the non-Dana
 *    tests (`CobExtendedCarbsTest`, `SetupWizardE2EHiltTest`, `LoopTest`, the reconciler, …)
 *
 * To rebalance: tag a heavy class `@ShardA` to move it to A, or drop `@ShardA` to move it to B. No CI
 * change needed — only the annotations move. Verify against the per-shard times in a CI run.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardA
