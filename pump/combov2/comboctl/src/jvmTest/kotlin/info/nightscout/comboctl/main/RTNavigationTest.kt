package info.nightscout.comboctl.main

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.NullDisplayFrame
import info.nightscout.comboctl.base.PathSegment
import info.nightscout.comboctl.base.findShortestPath
import info.nightscout.comboctl.base.testUtils.runBlockingWithWatchdog
import info.nightscout.comboctl.parser.AlertScreenContent
import info.nightscout.comboctl.parser.AlertScreenException
import info.nightscout.comboctl.parser.BatteryState
import info.nightscout.comboctl.parser.MainScreenContent
import info.nightscout.comboctl.parser.ParsedScreen
import info.nightscout.comboctl.parser.Quickinfo
import info.nightscout.comboctl.parser.ReservoirState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.BeforeAll
import kotlin.reflect.KClassifier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RTNavigationTest : TestBase() {
    /* RTNavigationContext implementation for testing out RTNavigation functionality.
     * This simulates the activity of a Combo in RT mode by using a defined list
     * of ParsedScreen instances. This context runs a coroutine that keeps emitting
     * screens from the list. Initially, it regularly emits the first ParsedScreen
     * from the list. If automaticallyAdvanceScreens is true, it will move on to
     * the next screen after a short while, otherwise it will stay at the same screen
     * in the testParsedScreenList until a button is pressed. The actual button type
     * is not evaluated; any button press advances the list iterator and causes the
     * next screen to be emitted through the SharedFlow. The flow is set up such
     * that it suspends when there are subscribers to make sure they don't miss
     * any screens. When there are no subscribers, it just keeps repeating the
     * same screen, so no screens are missed then either.
     *
     * This simulates the Combo's RT mode in the following ways: Often, the screen
     * doesn't actually change in a meaningful manner (for example when it blinks).
     * And, in most cases, a real change in the RT screen contents only happens
     * after user interaction (= a button press). automaticallyAdvanceScreens is
     * in fact false by default because its behavior is not commonly encountered.
     * That property is only used when testing waitUntilScreenAppears().
     */
    class TestRTNavigationContext(
        testParsedScreenList: List<ParsedScreen>,
        private val automaticallyAdvanceScreens: Boolean = false
    ) : RTNavigationContext {

        private val mainJob = SupervisorJob()
        private val mainScope = CoroutineScope(mainJob)
        private val testParsedScreenListIter = testParsedScreenList.listIterator()
        private var currentParsedScreen = testParsedScreenListIter.next()
        private val parsedScreenChannel = Channel<ParsedScreen?>(capacity = Channel.RENDEZVOUS)
        private var longButtonJob: Job? = null
        private var lastParsedScreen: ParsedScreen? = null

        val shortPressedRTButtons = mutableListOf<RTNavigationButton>()

        init {
            mainScope.launch {
                while (true) {
                    println("Emitting test screen $currentParsedScreen")
                    parsedScreenChannel.send(currentParsedScreen)
                    delay(100)
                    if (automaticallyAdvanceScreens) {
                        if (testParsedScreenListIter.hasNext())
                            currentParsedScreen = testParsedScreenListIter.next()
                        else
                            break
                    }
                }
            }
        }

        override val maxNumCycleAttempts: Int = 20

        override fun resetDuplicate() {
            lastParsedScreen = null
        }

        override suspend fun getParsedDisplayFrame(filterDuplicates: Boolean, processAlertScreens: Boolean): ParsedDisplayFrame? {
            while (true) {
                val thisParsedScreen = parsedScreenChannel.receive()

                if (filterDuplicates && (lastParsedScreen != null) && (thisParsedScreen != null)) {
                    if (lastParsedScreen == thisParsedScreen) {
                        currentParsedScreen = testParsedScreenListIter.next()
                        continue
                    }
                }

                lastParsedScreen = thisParsedScreen

                if ((thisParsedScreen != null) && thisParsedScreen.isBlinkedOut)
                    continue

                if (processAlertScreens && (thisParsedScreen != null)) {
                    if (thisParsedScreen is ParsedScreen.AlertScreen)
                        throw AlertScreenException(thisParsedScreen.content)
                }

                return thisParsedScreen?.let { ParsedDisplayFrame(NullDisplayFrame, it) }
            }
        }

        override suspend fun startLongButtonPress(button: RTNavigationButton, keepGoing: (suspend () -> Boolean)?) {
            longButtonJob = mainScope.launch {
                while (true) {
                    // The keepGoing() predicate can suspend this coroutine for a while.
                    // This is OK and expected. By definition, every time this predicate
                    // returns true, the long RT button press "keeps going". At each
                    // iteration with a predicate return value being true, the long RT
                    // button press is signaled to the Combo, which induces a change
                    // in the Combo, for example a quantity increment. We simulate this
                    // here by moving to the next screen if keepGoing() returns true.
                    // If keepGoing is not set, this behaves as if keepGoing() always
                    // returned true.
                    if (keepGoing?.let { !it() } == true)
                        break
                    currentParsedScreen = testParsedScreenListIter.next()
                }
            }
        }

        override suspend fun stopLongButtonPress() {
            longButtonJob?.cancelAndJoin()
            longButtonJob = null
        }

        override suspend fun waitForLongButtonPressToFinish() {
            longButtonJob?.join()
            longButtonJob = null
        }

        override suspend fun shortPressButton(button: RTNavigationButton) {
            // Simulate the consequences of user interaction by moving to the next screen.
            currentParsedScreen = testParsedScreenListIter.next()
            println("Moved to next screen $currentParsedScreen after short button press")
            shortPressedRTButtons.add(button)
        }
    }

    companion object {

        @BeforeAll
        @JvmStatic
        fun commonInit() {
            Logger.threshold = LogLevel.VERBOSE
        }
    }

    @Test
    fun checkRTNavigationGraphConnectivity() {
        // Check the rtNavigationGraph's connectivity. All nodes are
        // supposed to be connected and reachable from other nodes.

        val screenNodes = rtNavigationGraph.nodes

        for (nodeA in screenNodes.values) {
            for (nodeB in screenNodes.values) {
                // Skip this case, since the nodes in this
                // graph have no self-edges.
                if (nodeA === nodeB)
                    continue

                val path = rtNavigationGraph.findShortestPath(nodeA, nodeB)
                assertTrue(path!!.isNotEmpty())
            }
        }
    }

    @Test
    fun checkRTNavigationGraphPathFromMainScreenToBasalRateFactorSettingScreen() {
        val path = findShortestRtPath(
            ParsedScreen.MainScreen::class,
            ParsedScreen.BasalRateFactorSettingScreen::class,
            isComboStopped = false
        )

        assertNotNull(path)
        assertEquals(5, path.size)
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.TemporaryBasalRateMenuScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[0]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.MyDataMenuScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[1]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.BasalRate1ProgrammingMenuScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[2]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.BasalRateTotalScreen::class, RTEdgeValue(RTNavigationButton.CHECK)
            ), path[3]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.BasalRateFactorSettingScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[4]
        )
    }

    @Test
    fun checkRTNavigationGraphPathFromMainScreenToBasalRateFactorSettingScreenWhenStopped() {
        // The TBR menu is disabled when the Combo is stopped. We expect the
        // RT navigation to take that into account and find a shortest path
        // that does not include the TBR menu screen.

        val path = findShortestRtPath(
            ParsedScreen.MainScreen::class,
            ParsedScreen.BasalRateFactorSettingScreen::class,
            isComboStopped = true
        )

        assertNotNull(path)
        assertEquals(4, path.size)
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.MyDataMenuScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[0]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.BasalRate1ProgrammingMenuScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[1]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.BasalRateTotalScreen::class, RTEdgeValue(RTNavigationButton.CHECK)
            ), path[2]
        )
        assertEquals(
            PathSegment<KClassifier, RTEdgeValue>(
                ParsedScreen.BasalRateFactorSettingScreen::class, RTEdgeValue(RTNavigationButton.MENU)
            ), path[3]
        )
    }

    @Test
    fun checkComputeShortRTButtonPressWithOneStepSize() {
        // Test that computeShortRTButtonPress() correctly computes
        // the number of necessary short RT button presses and figures
        // out the correct button to press. These are the tests for
        // increment step arrays with one item.

        var result: Pair<Int, RTNavigationButton>

        result = computeShortRTButtonPress(
            currentQuantity = 100,
            targetQuantity = 130,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 3)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((130 - 100) / 3, RTNavigationButton.UP), result)

        result = computeShortRTButtonPress(
            currentQuantity = 4000,
            targetQuantity = 60,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 20)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((4000 - 60) / 20, RTNavigationButton.DOWN), result)

        result = computeShortRTButtonPress(
            currentQuantity = 10,
            targetQuantity = 20,
            cyclicQuantityRange = 60,
            incrementSteps = arrayOf(Pair(0, 1)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((20 - 10) / 1, RTNavigationButton.UP), result)

        // Tests that the cyclic quantity range is respected.
        // In this case, a wrap-around is expected to be preferred
        // by computeShortRTButtonPress().
        result = computeShortRTButtonPress(
            currentQuantity = 10,
            targetQuantity = 50,
            cyclicQuantityRange = 60,
            incrementSteps = arrayOf(Pair(0, 1)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(((60 - 50) + (10 - 0)) / 1, RTNavigationButton.DOWN), result)

        // Another cyclic quantity range test with a wrap around,
        // this time from the other direction (wrapping around
        // from quantity 58 to target quantity 1).
        result = computeShortRTButtonPress(
            currentQuantity = 58,
            targetQuantity = 1,
            cyclicQuantityRange = 60,
            incrementSteps = arrayOf(Pair(0, 1)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(3, RTNavigationButton.UP), result)

        // Additional test to check that cyclic ranges are handled correctly
        // even if currentQuantity is slightly higher than targetQuantity. This
        // verifies that the computation does not produce negative distances.
        result = computeShortRTButtonPress(
            currentQuantity = 11,
            targetQuantity = 10,
            cyclicQuantityRange = 24,
            incrementSteps = arrayOf(Pair(0, 1)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(1, RTNavigationButton.DOWN), result)

        // Test that computeShortRTButtonPress() can correctly handle start
        // quantities that aren't an integer multiple of the step size. The
        // "half-step" should be counted as one full step.

        result = computeShortRTButtonPress(
            currentQuantity = 25,
            targetQuantity = 40,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 20)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(1, RTNavigationButton.UP), result)

        result = computeShortRTButtonPress(
            currentQuantity = 25,
            targetQuantity = 60,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 20)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(2, RTNavigationButton.UP), result)

        result = computeShortRTButtonPress(
            currentQuantity = 35,
            targetQuantity = 20,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 20)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(1, RTNavigationButton.DOWN), result)

        result = computeShortRTButtonPress(
            currentQuantity = 55,
            targetQuantity = 20,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 20)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(2, RTNavigationButton.DOWN), result)

        // Corner case: current and target quantity are the same. In this case,
        // no RT button would actually be pressed, but the button value in the
        // result can't be left unset, so it is just set to CHECK. (Any value
        // would be okay; CHECK was chosen because it seems closest to something
        // like a "neutral" value.)
        result = computeShortRTButtonPress(
            currentQuantity = 60,
            targetQuantity = 60,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 20)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair(0, RTNavigationButton.CHECK), result)
    }

    @Test
    fun checkComputeShortRTButtonPressWithTwoStepSizes() {
        // Test that computeShortRTButtonPress() correctly computes
        // the number of necessary short RT button presses and figures
        // out the correct button to press. These are the tests for
        // increment step arrays with two items.

        var result: Pair<Int, RTNavigationButton>

        result = computeShortRTButtonPress(
            currentQuantity = 100,
            targetQuantity = 150,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((150 - 100) / 10, RTNavigationButton.UP), result)

        result = computeShortRTButtonPress(
            currentQuantity = 1000,
            targetQuantity = 1100,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((1100 - 1000) / 50, RTNavigationButton.UP), result)

        result = computeShortRTButtonPress(
            currentQuantity = 900,
            targetQuantity = 1050,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((1000 - 900) / 10 + (1050 - 1000) / 50, RTNavigationButton.UP), result)

        result = computeShortRTButtonPress(
            currentQuantity = 300,
            targetQuantity = 230,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((300 - 230) / 10, RTNavigationButton.DOWN), result)

        result = computeShortRTButtonPress(
            currentQuantity = 1200,
            targetQuantity = 1000,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((1200 - 1000) / 50, RTNavigationButton.DOWN), result)

        result = computeShortRTButtonPress(
            currentQuantity = 1100,
            targetQuantity = 970,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((1000 - 970) / 10 + (1100 - 1000) / 50, RTNavigationButton.DOWN), result)
    }

    @Test
    fun checkComputeShortRTButtonPressWithThreeStepSizes() {
        // Test that computeShortRTButtonPress() correctly computes
        // the number of necessary short RT button presses and figures
        // out the correct button to press. These are the tests for
        // increment step arrays with three items.

        var result: Pair<Int, RTNavigationButton>

        result = computeShortRTButtonPress(
            currentQuantity = 7900,
            targetQuantity = 710,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 50), Pair(50, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((1000 - 710) / 10 + (7900 - 1000) / 50, RTNavigationButton.DOWN), result)

        result = computeShortRTButtonPress(
            currentQuantity = 0,
            targetQuantity = 1100,
            cyclicQuantityRange = null,
            incrementSteps = arrayOf(Pair(0, 50), Pair(50, 10), Pair(1000, 50)),
            incrementButton = RTNavigationButton.UP,
            decrementButton = RTNavigationButton.DOWN
        )
        assertEquals(Pair((50 - 0) / 50 + (1000 - 50) / 10 + (1100 - 1000) / 50, RTNavigationButton.UP), result)
    }

    @Test
    fun checkRTNavigationFromMainToQuickinfo() {
        // Check RT screen navigation by navigating from the main screen
        // to the quickinfo screen. If this does not work properly, the
        // navigateToRTScreen() throws an exception or never ends. In
        // the latter case, the watchdog will eventually cancel the
        // coroutine and report the test as failed.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.QuickinfoMainScreen(Quickinfo(availableUnits = 105, reservoirState = ReservoirState.FULL))
            )
        )

        runBlockingWithWatchdog(12000) {
            navigateToRTScreen(rtNavigationContext, ParsedScreen.QuickinfoMainScreen::class, isComboStopped = false)
        }
    }

    @Test
    fun checkRTNavigationWithBlinkedOutScreens() {
        // During navigation, the stream must skip screens that are blinked out,
        // otherwise the navigation may incorrectly press RT buttons more often
        // than necessary.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.TemporaryBasalRateMenuScreen,
                ParsedScreen.TemporaryBasalRatePercentageScreen(percentage = 110, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(percentage = null, remainingDurationInMinutes = null),
                ParsedScreen.TemporaryBasalRateDurationScreen(durationInMinutes = 45)
            )
        )

        runBlockingWithWatchdog(12000) {
            navigateToRTScreen(rtNavigationContext, ParsedScreen.TemporaryBasalRateDurationScreen::class, isComboStopped = false)
        }

        assertContentEquals(
            listOf(
                RTNavigationButton.MENU,
                RTNavigationButton.CHECK,
                RTNavigationButton.MENU
            ),
            rtNavigationContext.shortPressedRTButtons
        )
    }

    @Test
    fun checkRTNavigationWhenAlreadyAtTarget() {
        // Check edge case handling when we want to navigate to
        // a target screen type, but we are in fact already there.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                )
            )
        )

        runBlockingWithWatchdog(12000) {
            navigateToRTScreen(rtNavigationContext, ParsedScreen.MainScreen::class, isComboStopped = false)
        }
    }

    @Test
    fun checkRTNavigationFromMainScreenToBasalRateFactorSettingScreen() {
        // Check the result of a more complex navigation.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 0, month = 1, day = 1, hour = 23, minute = 11),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 800,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.StopPumpMenuScreen,
                ParsedScreen.StandardBolusMenuScreen,
                ParsedScreen.ExtendedBolusMenuScreen,
                ParsedScreen.MultiwaveBolusMenuScreen,
                ParsedScreen.TemporaryBasalRateMenuScreen,
                ParsedScreen.MyDataMenuScreen,
                ParsedScreen.BasalRateProfileSelectionMenuScreen,
                ParsedScreen.BasalRate1ProgrammingMenuScreen,
                ParsedScreen.BasalRateTotalScreen(1840, 1),
                ParsedScreen.BasalRateFactorSettingScreen(
                    LocalDateTime(year = 0, month = 1, day = 1, hour = 0, minute = 0),
                    LocalDateTime(year = 0, month = 1, day = 1, hour = 1, minute = 0),
                    1000,
                    1
                )
            )
        )

        runBlockingWithWatchdog(12000) {
            val targetScreen = navigateToRTScreen(
                rtNavigationContext,
                ParsedScreen.BasalRateFactorSettingScreen::class,
                isComboStopped = false
            )
            assertIs<ParsedScreen.BasalRateFactorSettingScreen>(targetScreen)
        }

        // Navigation is done by pressing MENU 9 times until the basal rate
        // 1 programming menu is reached. The programming menu is entered
        // by pressing CHECK, after which the basal rate totals screen
        // shows up. Pressing MENU again enters further and shows the
        // first basal profile factor, which is the target the navigation
        // is trying to reach.
        val expectedShortRTButtonPressSequence = listOf(
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.MENU,
            RTNavigationButton.CHECK,
            RTNavigationButton.MENU
        )

        assertContentEquals(expectedShortRTButtonPressSequence, rtNavigationContext.shortPressedRTButtons)
    }

    @Test
    fun checkRTNavigationFromMainToQuickinfoWithChangingRemainingTbrDuration() {
        // Check that RT screen navigation skips a newly received screen
        // if it is of the same type as the previously observed screen.
        // This typically happens because a quantity like the remaining
        // TBR duration changes on screen. The navigation code has to
        // check that the _type_ of the screen changed, and if not, it
        // must skip the screen. Here, we simulate a main TBR screen
        // whose remaining TBR duration changes. We expect the navigation
        // code to detect this and press CHECK just _once_ (because the
        // second TBR main screen is skipped by the detection). Without
        // the screen type check, it would press CHECK _twice_.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Tbr(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        remainingTbrDurationInMinutes = 28,
                        tbrPercentage = 110,
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.MainScreen(
                    MainScreenContent.Tbr(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        remainingTbrDurationInMinutes = 27,
                        tbrPercentage = 110,
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.QuickinfoMainScreen(Quickinfo(availableUnits = 105, reservoirState = ReservoirState.FULL))
            )
        )

        runBlockingWithWatchdog(12000) {
            navigateToRTScreen(rtNavigationContext, ParsedScreen.QuickinfoMainScreen::class, isComboStopped = false)
        }

        val expectedShortRTButtonPressSequence = listOf(
            RTNavigationButton.CHECK
        )

        assertContentEquals(expectedShortRTButtonPressSequence, rtNavigationContext.shortPressedRTButtons)
    }

    @Test
    fun checkLongPressRTButtonUntil() {
        // Test long RT button presses by simulating transitions
        // between screens that happen due to the long button
        // press. The transition goes from the main screen
        // over basal rate 1-3 programming screens up to the
        // 4th one, which is the target. To test for "overshoots",
        // there's a 5th one after that.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.BasalRate1ProgrammingMenuScreen,
                ParsedScreen.BasalRate2ProgrammingMenuScreen,
                ParsedScreen.BasalRate3ProgrammingMenuScreen,
                ParsedScreen.BasalRate4ProgrammingMenuScreen,
                ParsedScreen.BasalRate5ProgrammingMenuScreen
            )
        )

        runBlockingWithWatchdog(12000) {
            val finalScreen = longPressRTButtonUntil(rtNavigationContext, RTNavigationButton.MENU) { parsedScreen ->
                if (parsedScreen is ParsedScreen.BasalRate4ProgrammingMenuScreen)
                    LongPressRTButtonsCommand.ReleaseButton
                else
                    LongPressRTButtonsCommand.ContinuePressingButton
            }
            assertIs<ParsedScreen.BasalRate4ProgrammingMenuScreen>(finalScreen)
        }
    }

    @Test
    fun checkShortPressRTButtonUntil() {
        // Test short RT button presses by simulating transitions
        // between screens that happen due to repeated short
        // button presses. The transition goes from the main screen
        // over basal rate 1-3 programming screens up to the
        // 4th one, which is the target. To test for "overshoots",
        // there's a 5th one after that.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.BasalRate1ProgrammingMenuScreen,
                ParsedScreen.BasalRate2ProgrammingMenuScreen,
                ParsedScreen.BasalRate3ProgrammingMenuScreen,
                ParsedScreen.BasalRate4ProgrammingMenuScreen,
                ParsedScreen.BasalRate5ProgrammingMenuScreen
            )
        )

        runBlockingWithWatchdog(12000) {
            val finalScreen = shortPressRTButtonsUntil(rtNavigationContext) { parsedScreen ->
                if (parsedScreen is ParsedScreen.BasalRate4ProgrammingMenuScreen)
                    ShortPressRTButtonsCommand.Stop
                else
                    ShortPressRTButtonsCommand.PressButton(RTNavigationButton.MENU)
            }
            assertIs<ParsedScreen.BasalRate4ProgrammingMenuScreen>(finalScreen)
        }
    }

    @Test
    fun checkLongPressRTButtonInnerAbort() {
        // Modified checkLongPressRTButtonUntil() test with a W6 warning
        // screen in between. We except the long button press to be aborted
        // and an AlertScreenException to be thrown.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.BasalRate1ProgrammingMenuScreen,
                ParsedScreen.BasalRate2ProgrammingMenuScreen,
                ParsedScreen.AlertScreen(AlertScreenContent.Warning(code = 6, AlertScreenContent.AlertScreenState.TO_SNOOZE)),
                ParsedScreen.BasalRate3ProgrammingMenuScreen,
                ParsedScreen.BasalRate4ProgrammingMenuScreen,
                ParsedScreen.BasalRate5ProgrammingMenuScreen
            )
        )

        runBlockingWithWatchdog(12000) {
            val e = assertFailsWith<AlertScreenException> {
                // Keep long-pressing the button. Eventually, the W6 screen is received.
                longPressRTButtonUntil(rtNavigationContext, RTNavigationButton.MENU) { parsedScreen ->
                    if (parsedScreen is ParsedScreen.BasalRate4ProgrammingMenuScreen)
                        LongPressRTButtonsCommand.ReleaseButton
                    else
                        LongPressRTButtonsCommand.ContinuePressingButton
                }
            }
            assertIs<AlertScreenContent.Warning>(e.alertScreenContent)
            assertEquals(6, e.alertScreenContent.code)
            // Simulate a short RT button press that would be used after the exception
            // was thrown to dismiss the W6 warning. This also checks that the long
            // button press has been finished correctly; if not, this call may fail
            // because the code incorrectly thinks that a long press button job
            // is still ongoing.
            rtNavigationContext.shortPressButton(RTNavigationButton.CHECK)
        }
    }

    @Test
    fun checkCycleToRTScreen() {
        // Test the cycleToRTScreen() by letting it repeatedly
        // press MENU until it reaches basal rate programming screen 4
        // in our simulated sequence of screens.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.MainScreen(
                    MainScreenContent.Normal(
                        currentTime = LocalDateTime(year = 2020, month = 10, day = 4, hour = 0, minute = 0),
                        activeBasalProfileNumber = 1,
                        currentBasalRateFactor = 300,
                        batteryState = BatteryState.FULL_BATTERY
                    )
                ),
                ParsedScreen.BasalRate1ProgrammingMenuScreen,
                ParsedScreen.BasalRate2ProgrammingMenuScreen,
                ParsedScreen.BasalRate3ProgrammingMenuScreen,
                ParsedScreen.BasalRate4ProgrammingMenuScreen,
                ParsedScreen.BasalRate5ProgrammingMenuScreen
            )
        )

        runBlockingWithWatchdog(12000) {
            val finalScreen = cycleToRTScreen(
                rtNavigationContext,
                RTNavigationButton.MENU,
                ParsedScreen.BasalRate4ProgrammingMenuScreen::class
            )
            assertIs<ParsedScreen.BasalRate4ProgrammingMenuScreen>(finalScreen)
        }
    }

    @Test
    fun checkWaitUntilScreenAppears() {
        // Test waitUntilScreenAppears() by letting the context itself
        // advance the screens until the screen that the function is
        // waiting for is reached.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.BasalRate1ProgrammingMenuScreen,
                ParsedScreen.BasalRate2ProgrammingMenuScreen,
                ParsedScreen.BasalRate3ProgrammingMenuScreen,
                ParsedScreen.BasalRate4ProgrammingMenuScreen
            ),
            automaticallyAdvanceScreens = true
        )

        runBlockingWithWatchdog(12000) {
            val finalScreen = waitUntilScreenAppears(
                rtNavigationContext,
                ParsedScreen.BasalRate3ProgrammingMenuScreen::class
            )
            assertIs<ParsedScreen.BasalRate3ProgrammingMenuScreen>(finalScreen)
        }
    }

    @Test
    fun checkAdjustQuantityOnScreen() {
        // Test adjustQuantityOnScreen() by simulating a sequence of screens
        // with a changing percentage quantity. This also simulates an overshoot
        // by jumping from 150 straight to 170, past the target of 160. We
        // expect adjustQuantityOnScreen() to catch this and correct it
        // using short RT button presses until the target quantity is observed.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.TemporaryBasalRatePercentageScreen(100, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(110, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(120, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(130, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(140, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(150, remainingDurationInMinutes = 30),
                // No 160 quantity here, on purpose, to test overshoot handling
                ParsedScreen.TemporaryBasalRatePercentageScreen(170, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(170, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(170, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(160, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(160, remainingDurationInMinutes = 30)
            )
        )

        runBlockingWithWatchdog(12000) {
            adjustQuantityOnScreen(
                rtNavigationContext,
                targetQuantity = 160,
                cyclicQuantityRange = null,
                incrementSteps = arrayOf(Pair(0, 10))
            ) { parsedScreen ->
                parsedScreen as ParsedScreen.TemporaryBasalRatePercentageScreen
                parsedScreen.percentage
            }
        }
    }

    @Test
    fun checkAdjustQuantityOnScreenWithW6WarningDuringShortButtonPresses() {
        // Modified checkAdjustQuantityOnScreen() variant with a W6 warning screen in between
        // the screens that are received by the code when it corrects a long button press
        // overshoot with a number of short button presses. We expect an AlertScreenException
        // to be thrown. Such a warning screen interrupts and aborts whatever operation we
        // were doing and returns the Combo back to the main screen.

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.TemporaryBasalRatePercentageScreen(100, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(110, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(120, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(130, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(140, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(150, remainingDurationInMinutes = 30),
                // No 160 quantity here, on purpose, to test overshoot handling.
                // During the screens below, short button presses will be used
                // to fix the overshoot, so we place the W6 in between these
                // to test that the AlertScreenException is correctly thrown
                // while short-pressing the button.
                ParsedScreen.TemporaryBasalRatePercentageScreen(170, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(170, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(170, remainingDurationInMinutes = 30),
                ParsedScreen.AlertScreen(AlertScreenContent.Warning(code = 6, AlertScreenContent.AlertScreenState.TO_SNOOZE)),
                ParsedScreen.TemporaryBasalRatePercentageScreen(160, remainingDurationInMinutes = 30),
                ParsedScreen.TemporaryBasalRatePercentageScreen(160, remainingDurationInMinutes = 30)
            )
        )

        val e = assertFailsWith<AlertScreenException> {
            runBlockingWithWatchdog(12000) {
                adjustQuantityOnScreen(
                    rtNavigationContext,
                    targetQuantity = 160,
                    cyclicQuantityRange = null,
                    incrementSteps = arrayOf(Pair(0, 10))
                ) { parsedScreen ->
                    parsedScreen as ParsedScreen.TemporaryBasalRatePercentageScreen
                    parsedScreen.percentage
                }
            }
        }
        assertIs<AlertScreenContent.Warning>(e.alertScreenContent)
        assertEquals(6, e.alertScreenContent.code)
    }

    @Test
    fun checkCyclicAdjustQuantityOnScreen() {
        // Similar to checkAdjustQuantityOnScreen(), except that we
        // test a "cyclic" quantity here, meaning that there is a maximum
        // quantity and the current quantity can wrap around it back to 0.
        // In here, we simulate an adjustment with starting quantity 58
        // and target quantity 2, and a range of 0-60. Expected behavior
        // is that adjustQuantityOnScreen() increments and correctly
        // handles the wraparound from 59 to 0, since thanks to the
        // wraparound, incrementing the quantity is actually faster
        // than decrementing it. (With wrapround it goes 58 -> 0 -> 2
        // by incrementing, which is a total distance of 5 steps, while
        // without wraparound, it goes 58 -> 2 by decrementing, which
        // is a total distance of 55 steps.)

        val rtNavigationContext = TestRTNavigationContext(
            listOf(
                ParsedScreen.TimeAndDateSettingsMinuteScreen(58),
                ParsedScreen.TimeAndDateSettingsMinuteScreen(59),
                ParsedScreen.TimeAndDateSettingsMinuteScreen(0),
                ParsedScreen.TimeAndDateSettingsMinuteScreen(1),
                // No 2 quantity here, on purpose, to test overshoot handling
                ParsedScreen.TimeAndDateSettingsMinuteScreen(3),
                ParsedScreen.TimeAndDateSettingsMinuteScreen(2),
                // This is a dummy screen to avoid an exception due to the next() call in TestRTNavigationContext.shortButtonPress()
                ParsedScreen.TimeAndDateSettingsMinuteScreen(0)
            )
        )

        runBlockingWithWatchdog(12000) {
            adjustQuantityOnScreen(
                rtNavigationContext,
                targetQuantity = 2,
                cyclicQuantityRange = 60,
                incrementSteps = arrayOf(Pair(0, 1))
            ) { parsedScreen ->
                parsedScreen as ParsedScreen.TimeAndDateSettingsMinuteScreen
                parsedScreen.minute
            }
        }
    }
}
