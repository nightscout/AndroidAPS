package app.aaps.testcategories

import java.lang.annotation.Inherited

/**
 * Marks the pump emulator scenarios as **shard D**. Shard C leaves them out (`notAnnotation` lists A, B and D).
 *
 * [Inherited], and set on `AbstractPumpScenarioTest`, so every `<Pump>ScenarioTest` is in this shard without a tag of
 * its own. CI lists the classes of this shard and makes a work list of them (`.circleci/scenario-worker.sh`): each
 * class in parts, and its `appKilledDuringABolus` in two runs, the app restart that needs a new process (see
 * `.claude/skills/pump-emulator-scenarios/SKILL.md`). Every emulator takes items from that list, so a new pump needs no
 * CI change.
 */
@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardD
