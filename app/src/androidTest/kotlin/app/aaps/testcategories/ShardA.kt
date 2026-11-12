package app.aaps.testcategories

/**
 * Marks an instrumented test class as **shard A**. CI (`.circleci/config.yml`) splits the app module's androidTest
 * suite over its emulators, and the runner filters on these annotations:
 *  - **shard A** runs `-e annotation app.aaps.testcategories.ShardA`
 *  - **shard B** runs `-e annotation app.aaps.testcategories.ShardB` (see [ShardB])
 *  - **shard C** runs `-e notAnnotation app.aaps.testcategories.ShardA,app.aaps.testcategories.ShardB,app.aaps.testcategories.ShardD`,
 *    so everything else, also a new test with no annotation: no test can fall into no shard.
 *  - the pump emulator scenarios ([ShardD]) are a work list that every emulator takes from when its shard is done.
 *
 * What is where now:
 *  - **A** = `DanaRsEmulatorUiTest`, the Dana RS screens over all three handshakes
 *  - **B** = the DanaR family (`DanaRUiTest`), the Dana RS
 *    pump and pair wizard tests, and a few non-pump tests moved there to balance shard C
 *  - **C** = `SetupWizardE2ETest` (the long one), `CarelevoUiTest`, `EquilUiTest` and the other tests
 *
 * Balance is by measured time, not by test count. To rebalance, move a class between `@ShardA`, `@ShardB` and no
 * annotation; CI needs no change. Check the shard times of a CI run afterwards.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardA
