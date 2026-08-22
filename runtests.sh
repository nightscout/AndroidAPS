#!/bin/zsh
# allTests is needed as well as testFullDebugUnitTest: multiplatform modules have no build
# variants, so they have no testFullDebugUnitTest task and would silently run no tests at all.
./gradlew -Pcoverage -PfirebaseDisable testFullDebugUnitTest allTests
