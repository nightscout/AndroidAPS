package app.aaps.testcategories

/**
 * Marks an instrumented test class as **shard B** for the three-emulator CI split (see [ShardA]).
 * The app module's androidTest suite is filtered natively across three emulators:
 *  - **shard A** runs `-e annotation app.aaps.testcategories.ShardA`
 *  - **shard B** runs `-e annotation app.aaps.testcategories.ShardB`
 *  - **shard C** runs `-e notAnnotation app.aaps.testcategories.ShardA,app.aaps.testcategories.ShardB`
 *    — i.e. *everything else*, so a new, untagged test can never fall into no shard and be skipped.
 *
 * Balance is by measured time, not test count. The heavy tests dominate, so they are spread one family
 * per emulator:
 *  - **A** = the DanaRS suite (`DanaRsEmulatorUiTest`, `DanaRSPairWizardUiTest`, `DanaRsEmulatorPumpTest`,
 *    `DanaRsEmulatorTransportTest`)
 *  - **B** = the DanaR/RFCOMM family (`DanaREmulatorPumpTest`, `DanaRPairWizardUiTest`, `DanaREmulatorUiTest`)
 *    + the DanaRS pump/transport/pair-wizard tests + a couple of non-Dana tests (`CobExtendedCarbsTest`,
 *    `RunningModeReconcilerIntegrationTest`) pulled over purely to balance against shard C
 *  - **C** = `SetupWizardE2EHiltTest` (the ~284s long pole) + the remaining non-Dana tests (`LoopTest`, …)
 *
 * To rebalance: move a class between `@ShardA` / `@ShardB` / untagged. No CI change needed — only the
 * annotations move. Verify against the per-shard times in a CI run.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardB
