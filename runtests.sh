#!/bin/zsh
# All three task names are needed, and a missing one runs no tests instead of failing:
#  - testFullDebugUnitTest: the flavoured modules, which are only :app and the :wear ones.
#  - testDebugUnitTest: the Android libraries. They have no flavours, so there is no "Full" task.
#  - allTests: multiplatform modules, which have no build variants at all.
./gradlew -Pcoverage -PfirebaseDisable testFullDebugUnitTest testDebugUnitTest allTests
