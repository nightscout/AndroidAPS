package app.aaps.testcategories

import java.lang.annotation.Inherited

/**
 * Marks the pump emulator scenarios as **shard D**, run on a fourth CI emulator: `-e annotation
 * app.aaps.testcategories.ShardD`. Shard C leaves them out (`notAnnotation` lists A, B and D).
 *
 * [Inherited], and set on `AbstractPumpScenarioTest`, so every `<Pump>ScenarioTest` is in this shard without a tag of
 * its own. After the normal run, the CI step runs each class's `appKilledDuringABolus` in two runs, the app restart
 * that needs a new process (see `.claude/skills/pump-emulator-scenarios/SKILL.md`). It finds the classes by listing
 * this shard's tests, so a new pump needs no CI change either.
 */
@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardD
