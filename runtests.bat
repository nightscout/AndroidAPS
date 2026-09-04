@rem allTests is needed as well as testFullDebugUnitTest: multiplatform modules have no build
@rem variants, so they have no testFullDebugUnitTest task and would silently run no tests at all.
gradlew -Pcoverage -PfirebaseDisable testFullDebugUnitTest testDebugUnitTest allTests
