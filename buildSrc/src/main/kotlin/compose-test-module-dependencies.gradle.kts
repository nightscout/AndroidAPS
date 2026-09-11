/**
 * Opt-in convention plugin for modules that run Compose UI tests on the JVM via Robolectric.
 *
 * Kept separate from `test-module-dependencies` so Robolectric and the compose-ui-test artifacts
 * are pulled in only by modules that actually test Compose screens, not every test module.
 *
 * createComposeRule() is a JUnit4 TestRule and RobolectricTestRunner is a JUnit4 runner, so these
 * tests run JUnit4-style; the vintage engine bridges them onto the JUnit Platform alongside the
 * existing Jupiter tests (useJUnitPlatform() comes from test-module-dependencies).
 *
 * Pair with `jacoco-module-dependencies` for coverage of the Robolectric-loaded Compose classes.
 */
plugins {
    id("com.android.library")
}

testImplementationPlatformFromCatalog("androidx-compose-bom")
testImplementationFromCatalog("androidx-compose-ui-test-junit4")
testImplementationFromCatalog("org-robolectric")
testRuntimeOnlyFromCatalog("org-junit-vintage-engine")
debugImplementationFromCatalog("androidx-compose-ui-test-manifest")
