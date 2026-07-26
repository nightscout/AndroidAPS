package app.aaps.core.ui.compose

/**
 * Marks code that must be excluded from JaCoCo coverage reports.
 *
 * JaCoCo automatically ignores any class/method annotated with an annotation whose simple name
 * contains "Generated" and whose retention keeps it in the bytecode (BINARY or RUNTIME). We apply
 * this to Compose `@Preview` functions so preview-only code is not counted as uncovered. Unlike
 * Kover's annotation filter, this also works in the JaCoCo report that aggregates unit + connected
 * (instrumented) coverage — so a single report stays clean on Codecov.
 *
 * Use together with `@Preview` (this annotation does not replace it):
 * ```
 * @ExcludeFromJacocoGeneratedReport
 * @Preview(showBackground = true)
 * @Composable
 * private fun FooPreview() { ... }
 * ```
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class ExcludeFromJacocoGeneratedReport
