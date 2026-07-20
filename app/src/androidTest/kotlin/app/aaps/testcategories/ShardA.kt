package app.aaps.testcategories

/**
 * Marks an instrumented test class as **shard A** so CI can split the app module's androidTest suite
 * across two emulators. The runner filters natively on this:
 *  - **shard A** runs `-e annotation app.aaps.testcategories.ShardA`
 *  - **shard B** runs `-e notAnnotation app.aaps.testcategories.ShardA` — i.e. *everything else*,
 *    including any new, untagged test. Using the complement (not a `ShardB` annotation) means a test
 *    can never fall into neither shard and be silently skipped.
 *
 * Balance is by measured time, not test count:
 *  - **A** ≈ the Dana pump-emulator suite (`DanaRs*`/`DanaR*` ≈ 278s)
 *  - **B** ≈ `CobExtendedCarbsTest` + `SetupWizardE2EHiltTest` + the rest (≈ 277s)
 *
 * To rebalance later (e.g. the 2 upcoming emulator tests push B over): tag the new heavy class
 * `@ShardA`, or move a class off A. No CI change needed — only the annotations move.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardA
