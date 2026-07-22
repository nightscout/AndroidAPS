package app.aaps.testcategories

/**
 * Marks an instrumented test class as **shard A** so CI can split the app module's androidTest suite
 * across three emulators. The runner filters natively on the annotations:
 *  - **shard A** runs `-e annotation app.aaps.testcategories.ShardA`
 *  - **shard B** runs `-e annotation app.aaps.testcategories.ShardB` (see [ShardB])
 *  - **shard C** runs `-e notAnnotation app.aaps.testcategories.ShardA,app.aaps.testcategories.ShardB`
 *    — i.e. *everything else*, including any new, untagged test, so a test can never fall into no shard
 *    and be silently skipped.
 *
 * Balance is by measured time, not test count, and the aim is to keep each shard **under the unit-test
 * step** so the three shards hide beneath it (they run in the background while unit runs). Families are
 * spread one per emulator:
 *  - **A** = the DanaRS suite (`DanaRsEmulatorUiTest`, `DanaRSPairWizardUiTest`, `DanaRsEmulatorPumpTest`,
 *    `DanaRsEmulatorTransportTest`)
 *  - **B** = the DanaR/RFCOMM family (`DanaREmulatorPumpTest`, `DanaRPairWizardUiTest`, `DanaREmulatorUiTest`)
 *  - **C** = `SetupWizardE2EHiltTest` (the ~284s long pole) + the non-Dana tests
 *
 * To rebalance: move a class between `@ShardA` / `@ShardB` / untagged. No CI change needed — only the
 * annotations move. Verify against the per-shard times in a CI run.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardA
