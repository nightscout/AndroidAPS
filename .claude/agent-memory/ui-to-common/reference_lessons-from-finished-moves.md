---
name: lessons-from-finished-moves
description: What is still worth knowing from moves that are done (the :ui twelve-file batch, the aps Loop screen) - duplicate string names, Math operand order, proving the net twice, real resources in asserts, batching mutations
metadata:
  type: reference
---

The moves these come from are finished and their notes are deleted. These lessons still apply to the
next screen.

**A string name that exists in the module and in `:core:ui` belongs to the module.** `constraints` is
declared in both `plugins/aps/.../res/values/strings.xml` and `core/ui/.../res/values/strings.xml`.
Android resource merging gives the module's own string priority, so `R.string.constraints` inside
`:plugins:aps` was always the aps one, and `ApsStrings.constraints` is the faithful replacement, not
`CoreUiStrings.constraints`. The English text is the same, so a wrong pick shows only as a wrong
translation. Before choosing an owner for a name, grep both `strings.xml` files for it.

**Copy the JDK's own expressions for `Math`, and mind the operand order.** `Math.toDegrees(x)` is
`x * 180.0 / PI`, but `Math.toRadians(x)` is `x / 180.0 * PI` - **divide first**. `x * PI / 180.0`
is a different floating point expression. The two forms copied as they are give bit-identical
results to the JVM.

**Prove the net twice: before the move and after it.** Run the mutations against the files in
androidMain, then repeat the sharpest few against the same files **in commonMain**. Only the second
round shows that the string-resolution harness did not quietly break in the new source set.

**Assert against the real resource, never a hard coded string.** Read
`ctx.getString(R.string.format_mins, 45)` in `@Before` and compare with that. A translation edit then
cannot make the test lie.

**A `LaunchedEffect { while (true) { ...; delay(30_000) } }` does not hang `waitForIdle()`.** The
`TempTargetCarouselCard` progress loop looked like it would; it does not.

**Batch several mutations into one round only when each breaks a distinct test and none hides
another.** Otherwise one red test can hide that another mutation was never caught. The round itself
follows the "Mutation must be atomic" section of the agent definition.

Related: [[move-breaks-two-test-harnesses]], [[ui-screen-test-fixture]], [[cmp-what-crosses-unchanged]]
