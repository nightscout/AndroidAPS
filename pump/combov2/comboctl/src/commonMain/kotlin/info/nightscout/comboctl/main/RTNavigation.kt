package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.ApplicationLayer
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.Graph
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.PumpIO
import info.nightscout.comboctl.base.connectBidirectionally
import info.nightscout.comboctl.base.connectDirectionally
import info.nightscout.comboctl.base.findShortestPath
import info.nightscout.comboctl.base.getElapsedTimeInMs
import info.nightscout.comboctl.parser.ParsedScreen
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.reflect.KClassifier

private val logger = Logger.get("RTNavigation")

// There are _two_ waiting periods during RT button presses. The minimum one is there
// in case a new RT arrives very quickly; it is then necessary to wait a bit before
// sending the next button press packet to the Combo to avoid overflow. The maximum
// one is there if there is no screen update until we send the next button press
// packet to the Combo. Without the maximum period, we'd then end up in a deadlock.
// In other words, the maximum waiting period functions as a timeout.
private const val MINIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS = 110L
private const val MAXIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS = 600L
private const val MAX_NUM_SAME_QUANTITY_OBSERVATIONS = 10

/**
 * RT navigation buttons.
 *
 * These are essentially the [ApplicationLayer.RTButton] values, but
 * also include combined button presses for navigating back (which
 * requires pressing both MENU and UP buttons at the same time).
 */
enum class RTNavigationButton(val rtButtonCodes: List<ApplicationLayer.RTButton>) {
    UP(listOf(ApplicationLayer.RTButton.UP)),
    DOWN(listOf(ApplicationLayer.RTButton.DOWN)),
    MENU(listOf(ApplicationLayer.RTButton.MENU)),
    CHECK(listOf(ApplicationLayer.RTButton.CHECK)),

    BACK(listOf(ApplicationLayer.RTButton.MENU, ApplicationLayer.RTButton.UP)),
    UP_DOWN(listOf(ApplicationLayer.RTButton.UP, ApplicationLayer.RTButton.DOWN))
}

internal data class RTEdgeValue(val button: RTNavigationButton, val edgeValidityCondition: EdgeValidityCondition = EdgeValidityCondition.ALWAYS) {
    enum class EdgeValidityCondition {
        ONLY_IF_COMBO_STOPPED,
        ONLY_IF_COMBO_RUNNING,
        ALWAYS
    }

    // Exclude edgeValidityCondition from comparisons. This is mainly
    // done to make it easier to test the RT navigation code.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RTEdgeValue

        if (button != other.button) return false

        return true
    }

    override fun hashCode(): Int {
        return button.hashCode()
    }
}

// Directed cyclic graph for navigating between RT screens. The edge
// values indicate what button to press to reach the edge's target node
// (= target screen). The button may have to be pressed more than once
// until the target screen appears if other screens are in between.
internal val rtNavigationGraph = Graph<KClassifier, RTEdgeValue>().apply {
    // Set up graph nodes for each ParsedScreen, to be able
    // to connect them below.
    val mainNode = node(ParsedScreen.MainScreen::class)
    val quickinfoNode = node(ParsedScreen.QuickinfoMainScreen::class)
    val tbrMenuNode = node(ParsedScreen.TemporaryBasalRateMenuScreen::class)
    val tbrPercentageNode = node(ParsedScreen.TemporaryBasalRatePercentageScreen::class)
    val tbrDurationNode = node(ParsedScreen.TemporaryBasalRateDurationScreen::class)
    val myDataMenuNode = node(ParsedScreen.MyDataMenuScreen::class)
    val myDataBolusDataMenuNode = node(ParsedScreen.MyDataBolusDataScreen::class)
    val myDataErrorDataMenuNode = node(ParsedScreen.MyDataErrorDataScreen::class)
    val myDataDailyTotalsMenuNode = node(ParsedScreen.MyDataDailyTotalsScreen::class)
    val myDataTbrDataMenuNode = node(ParsedScreen.MyDataTbrDataScreen::class)
    val basalRate1MenuNode = node(ParsedScreen.BasalRate1ProgrammingMenuScreen::class)
    val basalRateTotalNode = node(ParsedScreen.BasalRateTotalScreen::class)
    val basalRateFactorSettingNode = node(ParsedScreen.BasalRateFactorSettingScreen::class)
    val timeDateSettingsMenuNode = node(ParsedScreen.TimeAndDateSettingsMenuScreen::class)
    val timeDateSettingsHourNode = node(ParsedScreen.TimeAndDateSettingsHourScreen::class)
    val timeDateSettingsMinuteNode = node(ParsedScreen.TimeAndDateSettingsMinuteScreen::class)
    val timeDateSettingsYearNode = node(ParsedScreen.TimeAndDateSettingsYearScreen::class)
    val timeDateSettingsMonthNode = node(ParsedScreen.TimeAndDateSettingsMonthScreen::class)
    val timeDateSettingsDayNode = node(ParsedScreen.TimeAndDateSettingsDayScreen::class)

    // Below, nodes are connected. Connections are edges in the graph.

    // Main screen and quickinfo.
    connectBidirectionally(RTEdgeValue(RTNavigationButton.CHECK), RTEdgeValue(RTNavigationButton.BACK), mainNode, quickinfoNode)

    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU), RTEdgeValue(RTNavigationButton.BACK),
        myDataMenuNode, basalRate1MenuNode
    )

    // Connection between main menu and time and date settings menu. Note that there
    // is only this one connection to the time and date settings menu, even though it
    // is actually possible to reach that menu from for example the basal rate 1
    // programming one by pressing MENU several times. That's because depending on
    // the Combo's configuration, significantly more menus may actually lie between
    // basal rate 1 and time and date settings, causing the navigation to take
    // significantly longer. Also, in pretty much all cases, any access to the time
    // and date settings menu starts from the main menu, so it makes sense to establish
    // only one connection between the main menu and the time and date settings menu.
    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.BACK), RTEdgeValue(RTNavigationButton.MENU),
        mainNode,
        timeDateSettingsMenuNode
    )

    // Connections to the TBR menu do not always exist - if the Combo
    // is stopped, the TBR menu is disabled, so create separate connections
    // for it and mark them as being invalid if the Combo is stopped to
    // prevent the RTNavigation code from traversing them if the Combo
    // is currently in the stopped state.

    // These are the TBR menu connections. In the running state, the
    // TBR menu is then directly reachable from the main menu and is
    // placed in between the main and the My Data menu.
    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        RTEdgeValue(RTNavigationButton.BACK, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        mainNode, tbrMenuNode
    )
    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        RTEdgeValue(RTNavigationButton.BACK, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING),
        tbrMenuNode, myDataMenuNode
    )

    // In the stopped state, the My Data menu can directly be reached from the
    // main mode, since the TBR menu that is in between is turned off.
    connectBidirectionally(
        RTEdgeValue(RTNavigationButton.MENU, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_STOPPED),
        RTEdgeValue(RTNavigationButton.BACK, RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_STOPPED),
        mainNode, myDataMenuNode
    )

    // These are the connections between TBR screens. A specialty of these
    // screens is that transitioning between the percentage and duration
    // screens is done with the MENU screen in both directions
    // (percentage->duration and duration->percentage). The TBR menu screen
    // can be reached from both of these screens by pressing BACK. But the
    // duration screen cannot be reached directly from the TBR menu screen,
    // which is why there's a direct edge from the duration to the menu
    // screen but not one in the other direction.
    connectBidirectionally(RTEdgeValue(RTNavigationButton.CHECK), RTEdgeValue(RTNavigationButton.BACK), tbrMenuNode, tbrPercentageNode)
    connectBidirectionally(RTEdgeValue(RTNavigationButton.MENU), RTEdgeValue(RTNavigationButton.MENU), tbrPercentageNode, tbrDurationNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), tbrDurationNode, tbrMenuNode)

    // The basal rate programming screens. Going to the basal rate factors requires
    // two transitions (basal rate 1 -> basal rate total -> basal rate factor).
    // Going back requires one, but directly goes back to basal rate 1.
    connectBidirectionally(RTEdgeValue(RTNavigationButton.CHECK), RTEdgeValue(RTNavigationButton.BACK), basalRate1MenuNode, basalRateTotalNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.MENU), basalRateTotalNode, basalRateFactorSettingNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), basalRateFactorSettingNode, basalRate1MenuNode)

    // Connections between myData screens. Navigation through these screens
    // is rather straightforward. Pressing CHECK when at the my data menu
    // transitions to the bolus data screen. Pressing MENU then transitions
    // through the various myData screens. The order is: bolus data, error
    // data, daily totals, TBR data. Pressing MENU when at the TBR data
    // screen cycles back to the bolus data screen. Pressing BACK in any
    // of these screens transitions back to the my data menu screen.
    connectDirectionally(RTEdgeValue(RTNavigationButton.CHECK), myDataMenuNode, myDataBolusDataMenuNode)
    connectDirectionally(
        RTEdgeValue(RTNavigationButton.MENU),
        myDataBolusDataMenuNode, myDataErrorDataMenuNode, myDataDailyTotalsMenuNode, myDataTbrDataMenuNode
    )
    connectDirectionally(RTEdgeValue(RTNavigationButton.MENU), myDataTbrDataMenuNode, myDataBolusDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataBolusDataMenuNode, myDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataErrorDataMenuNode, myDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataDailyTotalsMenuNode, myDataMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), myDataTbrDataMenuNode, myDataMenuNode)

    // Time and date settings screen. These work just like the my data screens.
    // That is: Navigating between the "inner" time and date screens works
    // by pressing MENU, and when pressing MENU at the last of these screens,
    // navigation transitions back to the first of these screens. Pressing
    // BACK transitions back to the time and date settings menu screen.
    connectDirectionally(RTEdgeValue(RTNavigationButton.CHECK), timeDateSettingsMenuNode, timeDateSettingsHourNode)
    connectDirectionally(
        RTEdgeValue(RTNavigationButton.MENU),
        timeDateSettingsHourNode, timeDateSettingsMinuteNode, timeDateSettingsYearNode,
        timeDateSettingsMonthNode, timeDateSettingsDayNode
    )
    connectDirectionally(RTEdgeValue(RTNavigationButton.MENU), timeDateSettingsDayNode, timeDateSettingsHourNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsHourNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsMinuteNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsYearNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsMonthNode, timeDateSettingsMenuNode)
    connectDirectionally(RTEdgeValue(RTNavigationButton.BACK), timeDateSettingsDayNode, timeDateSettingsMenuNode)
}

/**
 * Base class for exceptions thrown when navigating through remote terminal (RT) screens.
 *
 * @param message The detail message.
 */
open class RTNavigationException(message: String) : ComboException(message)

/**
 * Exception thrown when the RT navigation could not find a screen of the searched type.
 *
 * @property targetScreenType Type of the screen that was searched.
 */
class CouldNotFindRTScreenException(val targetScreenType: KClassifier) :
    RTNavigationException("Could not find RT screen $targetScreenType")

/**
 * Exception thrown when the RT navigation encountered an unexpected screen type.
 *
 * @property expectedScreenType Type of the screen that was expected.
 * @property encounteredScreenType Type of the screen that was encountered.
 */
class UnexpectedRTScreenException(
    val expectedScreenType: KClassifier,
    val encounteredScreenType: KClassifier
) : RTNavigationException("Unexpected RT screen; expected $expectedScreenType, encountered $encounteredScreenType")

/**
 * Exception thrown when in spite of repeatedly trying to exit to the main screen, no recognizable RT screen is found.
 *
 * This is different from [NoUsableRTScreenException] in that the code tried to get out
 * of whatever unrecognized part of the RT menu and failed because it kept seeing unfamiliar
 * screens, while that other exception is about not getting a specific RT screen.
 */
class CouldNotRecognizeAnyRTScreenException : RTNavigationException("Could not recognize any RT screen")

/**
 * Exception thrown when a function needed a specific screen type but could not get it.
 *
 * Typically, this happens because a display frame could not be parsed
 * (= the screen is [ParsedScreen.UnrecognizedScreen]).
 */
class NoUsableRTScreenException : RTNavigationException("No usable RT screen available")

/**
 * Exception thrown when [adjustQuantityOnScreen] attempts to adjust the shown quantity but hits an unexpected limit.
 *
 * For example, if the quantity shall be set to 500, but after incrementing it, it
 * suddenly stops incrementing at 200, this exception is thrown to alert the user
 * about this unexpected behavior.
 *
 * @param targetQuantity Quantity that was supposed to be reached.
 * @param hitLimitAt The quantity at which point adjustments stopped changing the quantity.
 */
class QuantityNotChangingException(
    val targetQuantity: Int,
    val hitLimitAt: Int
) : RTNavigationException("Attempted to adjust quantity to target value $targetQuantity, but hit limit at $hitLimitAt")

/**
 * Remote terminal (RT) navigation context.
 *
 * This provides the necessary functionality for functions that navigate through RT screens
 * like [cycleToRTScreen]. These functions analyze [ParsedScreen] instances contained
 * in incoming [ParsedDisplayFrame] ones, and apply changes & transitions with the provided
 * abstract button actions.
 *
 * The button press functions are almost exactly like the ones from [PumpIO]. The only
 * difference is how buttons are specified - the underlying PumpIO functions get the
 * [RTNavigationButton.rtButtonCodes] value of their "button" arguments, and not the
 * "button" argument directly.
 */
interface RTNavigationContext {
    /**
     * Maximum number of times functions like [cycleToRTScreen] can cycle through screens.
     *
     * This is a safeguard to prevent infinite loops in case these functions like [cycleToRTScreen]
     * fail to find the screen they are looking for. This is a quantity that defines how
     * often these functions can transition to other screens without getting to the screen
     * they are looking for. Past that amount, they throw [CouldNotFindRTScreenException].
     *
     * This is always >= 1, and typically a value like 20.
     */
    val maxNumCycleAttempts: Int

    fun resetDuplicate()

    suspend fun getParsedDisplayFrame(filterDuplicates: Boolean, processAlertScreens: Boolean = true): ParsedDisplayFrame?

    suspend fun startLongButtonPress(button: RTNavigationButton, keepGoing: (suspend () -> Boolean)? = null)
    suspend fun stopLongButtonPress()
    suspend fun waitForLongButtonPressToFinish()
    suspend fun shortPressButton(button: RTNavigationButton)
}

/**
 * [PumpIO] based implementation of [RTNavigationContext].
 *
 * This uses a [PumpIO] instance to pass button actions to, and provides a stream
 * of [ParsedDisplayFrame] instances. It is the implementation suited for
 * production use. [maxNumCycleAttempts] is set to 20 by default.
 */
class RTNavigationContextProduction(
    private val pumpIO: PumpIO,
    private val parsedDisplayFrameStream: ParsedDisplayFrameStream,
    override val maxNumCycleAttempts: Int = 20
) : RTNavigationContext {
    init {
        require(maxNumCycleAttempts > 0)
    }

    override fun resetDuplicate() = parsedDisplayFrameStream.resetDuplicate()

    override suspend fun getParsedDisplayFrame(filterDuplicates: Boolean, processAlertScreens: Boolean) =
        parsedDisplayFrameStream.getParsedDisplayFrame(filterDuplicates, processAlertScreens)

    override suspend fun startLongButtonPress(button: RTNavigationButton, keepGoing: (suspend () -> Boolean)?) =
        pumpIO.startLongRTButtonPress(button.rtButtonCodes, keepGoing)

    override suspend fun stopLongButtonPress() = pumpIO.stopLongRTButtonPress()

    override suspend fun waitForLongButtonPressToFinish() = pumpIO.waitForLongRTButtonPressToFinish()

    override suspend fun shortPressButton(button: RTNavigationButton) = pumpIO.sendShortRTButtonPress(button.rtButtonCodes)
}

sealed class ShortPressRTButtonsCommand {
    object DoNothing : ShortPressRTButtonsCommand()
    object Stop : ShortPressRTButtonsCommand()
    data class PressButton(val button: RTNavigationButton) : ShortPressRTButtonsCommand()
}

sealed class LongPressRTButtonsCommand {
    object ContinuePressingButton : LongPressRTButtonsCommand()
    object ReleaseButton : LongPressRTButtonsCommand()
}

/**
 * Holds down a specific button until the specified screen check callback returns true.
 *
 * This is useful for performing an ongoing activity based on the screen contents.
 * [adjustQuantityOnScreen] uses this internally for adjusting a quantity on screen.
 * [button] is kept pressed until [checkScreen] returns [LongPressRTButtonsCommand.ReleaseButton],
 * at which point that RT button is released.
 *
 * NOTE: The RT button may actually be released a little past the time [checkScreen]
 * indicates that the RT button is to be released. This is due to limitations in how
 * the RT screen UX works. It is recommended to add checks after running the long RT
 * button press if the state of the RT screen afterwards is important. For example,
 * when adjusting a quantity on the RT screen, check afterwards the quantity once it
 * stops in/decrementing and correct it with short RT button presses if needed.
 *
 * @param rtNavigationContext Context to use for the long RT button press.
 * @param button Button to long-press.
 * @param checkScreen Callback that returns whether to continue
 *   long-pressing the button or releasing it.
 * @return The last observed [ParsedScreen].
 * @throws info.nightscout.comboctl.parser.AlertScreenException if alert screens are seen.
 */
suspend fun longPressRTButtonUntil(
    rtNavigationContext: RTNavigationContext,
    button: RTNavigationButton,
    checkScreen: (parsedScreen: ParsedScreen) -> LongPressRTButtonsCommand
): ParsedScreen {
    lateinit var lastParsedScreen: ParsedScreen

    logger(LogLevel.DEBUG) { "Long-pressing RT button $button" }

    rtNavigationContext.resetDuplicate()

    var thrownDuringButtonPress: Throwable? = null

    rtNavigationContext.startLongButtonPress(button) {
        // Suspend the block until either we get a new parsed display frame
        // or WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS milliseconds
        // pass. In the latter case, we instruct startLongButtonPress()
        // to just continue pressing the button. In the former case,
        // we analyze the screen and act according to the result.
        // We use withTimeout(), because sometimes, the Combo may not
        // immediately return a frame just because we are pressing the
        // button. If we just wait for the next frame, we can then end
        // up waiting forever.

        val timestampBeforeDisplayFrameRetrieval = getElapsedTimeInMs()

        // Receive the parsedDisplayFrame, and if none is received or if
        // the timeout expires (parsedDisplayFrame gets set to null in
        // both cases), keep pressing the button.
        val parsedDisplayFrame = try {
            withTimeout(
                timeMillis = MAXIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS
            ) {
                rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true)
            }
        } catch (e: TimeoutCancellationException) {
            // Timeout expired, and we got no new frame. Stop waiting
            // for one and continue long-pressing the button. We might
            // be on a screen that does not update on its own.
            null
        } catch (t: Throwable) {
            // An exception that's not TimeoutCancellationException
            // was thrown. Catch it, store it to rethrow it later,
            // and end the long button press.
            thrownDuringButtonPress = t
            return@startLongButtonPress false
        } ?: return@startLongButtonPress true

        // It is possible that we got a parsed display frame very quickly.
        // Wait a while in such a case to avoid overrunning the Combo
        // with button press packets. In such a case, the Combo's ring
        // buffer would overflow, and an error would occur. (This seems
        // to be a phenomenon that is separate to the packet overflow
        // that is documented in TransportLayer.IO.sendInternal().)
        val elapsedTime = getElapsedTimeInMs() - timestampBeforeDisplayFrameRetrieval
        if (elapsedTime < MINIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS) {
            val waitingPeriodInMs = MINIMUM_WAIT_PERIOD_DURING_LONG_RT_BUTTON_PRESS_IN_MS - elapsedTime
            logger(LogLevel.VERBOSE) { "Waiting $waitingPeriodInMs milliseconds before continuing button long-press" }
            delay(timeMillis = waitingPeriodInMs)
        }

        // At this point, we got a non-null parsedDisplayFrame that we can
        // analyze. The analysis is done by checkScreen. If an exception
        // is thrown by that callback, catch and store it, stop pressing
        // the button, and exit. The code further below re-throws the
        // stored exception.
        val parsedScreen = parsedDisplayFrame.parsedScreen
        val predicateResult = try {
            checkScreen(parsedScreen)
        } catch (t: Throwable) {
            thrownDuringButtonPress = t
            return@startLongButtonPress false
        }

        // Proceed according to the result of checkScreen.
        val releaseButton = (predicateResult == LongPressRTButtonsCommand.ReleaseButton)
        logger(LogLevel.VERBOSE) {
            "Observed parsed screen $parsedScreen while long-pressing RT button; predicate result = $predicateResult"
        }
        if (releaseButton) {
            // Record the screen we just saw so we can return it.
            lastParsedScreen = parsedScreen
            return@startLongButtonPress false
        } else
            return@startLongButtonPress true
    }

    // The block that is passed to startLongButtonPress() runs in a
    // background coroutine. We wait here for that coroutine to finish.
    rtNavigationContext.waitForLongButtonPressToFinish()

    // Rethrow previously caught exception (if there was any).
    thrownDuringButtonPress?.let {
        logger(LogLevel.INFO) { "Rethrowing Throwable caught during long RT button press: $it" }
        throw it
    }

    logger(LogLevel.DEBUG) { "Long-pressing RT button $button stopped" }

    return lastParsedScreen
}

/**
 * Short-presses a button until the specified screen check callback returns true.
 *
 * This is the short-press counterpart to [longPressRTButtonUntil]. For each observed
 * [ParsedScreen], it invokes the specified  [processScreen] callback. That callback
 * then returns a command, telling this function what to do next. If that command is
 * [ShortPressRTButtonsCommand.PressButton], this function short-presses the button
 * specified in that sealed subclass, and then waits for the next [ParsedScreen].
 * If the command is [ShortPressRTButtonsCommand.Stop], this function finishes.
 * If the command is [ShortPressRTButtonsCommand.DoNothing], this function skips
 * the current [ParsedScreen]. The last command is useful for example when the
 * screen contents are blinking. By returning DoNothing, the callback effectively
 * causes this function to wait until another screen (hopefully without the blinking)
 * arrives and can be processed by that callback.
 *
 * @param rtNavigationContext Context to use for the short RT button press.
 * @param processScreen Callback that returns the command this function shall execute next.
 * @return The last observed [ParsedScreen].
 * @throws info.nightscout.comboctl.parser.AlertScreenException if alert screens are seen.
 */
suspend fun shortPressRTButtonsUntil(
    rtNavigationContext: RTNavigationContext,
    processScreen: (parsedScreen: ParsedScreen) -> ShortPressRTButtonsCommand
): ParsedScreen {
    logger(LogLevel.DEBUG) { "Repeatedly short-pressing RT button according to callback commands" }

    rtNavigationContext.resetDuplicate()

    while (true) {
        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        logger(LogLevel.VERBOSE) { "Got new screen $parsedScreen" }

        val command = processScreen(parsedScreen)
        logger(LogLevel.VERBOSE) { "Short-press RT button callback returned $command" }

        when (command) {
            ShortPressRTButtonsCommand.DoNothing -> Unit
            ShortPressRTButtonsCommand.Stop -> return parsedScreen
            is ShortPressRTButtonsCommand.PressButton -> rtNavigationContext.shortPressButton(command.button)
        }
    }
}

/**
 * Repeatedly presses the [button] until a screen of the required [targetScreenType] appears.
 *
 * @param rtNavigationContext Context for navigating to the target screen.
 * @param button Button to press for cycling to the target screen.
 * @param targetScreenType Type of the target screen.
 * @return The last observed [ParsedScreen].
 * @throws CouldNotFindRTScreenException if the screen was not found even
 *   after this function moved [RTNavigationContext.maxNumCycleAttempts]
 *   times from screen to screen.
 * @throws info.nightscout.comboctl.parser.AlertScreenException if alert screens are seen.
 */
suspend fun cycleToRTScreen(
    rtNavigationContext: RTNavigationContext,
    button: RTNavigationButton,
    targetScreenType: KClassifier
): ParsedScreen {
    logger(LogLevel.DEBUG) { "Running shortPressRTButtonsUntil() until screen of type $targetScreenType is observed" }
    var cycleCount = 0
    return shortPressRTButtonsUntil(rtNavigationContext) { parsedScreen ->
        if (cycleCount >= rtNavigationContext.maxNumCycleAttempts)
            throw CouldNotFindRTScreenException(targetScreenType)

        when (parsedScreen::class) {
            targetScreenType -> {
                logger(LogLevel.DEBUG) { "Target screen of type $targetScreenType reached; cycleCount = $cycleCount" }
                ShortPressRTButtonsCommand.Stop
            }
            else -> {
                cycleCount++
                logger(LogLevel.VERBOSE) { "Did not yet reach target screen type; cycleCount increased to $cycleCount" }
                ShortPressRTButtonsCommand.PressButton(button)
            }
        }
    }
}

/**
 * Keeps watching out for incoming screens until one of the desired type is observed.
 *
 * @param rtNavigationContext Context for observing incoming screens.
 * @param targetScreenType Type of the target screen.
 * @return The last observed [ParsedScreen], which is the screen this
 *   function was waiting for.
 * @throws CouldNotFindRTScreenException if the screen was not seen even after
 *   this function observed [RTNavigationContext.maxNumCycleAttempts]
 *   screens coming in.
 * @throws info.nightscout.comboctl.parser.AlertScreenException if alert screens are seen.
 */
suspend fun waitUntilScreenAppears(
    rtNavigationContext: RTNavigationContext,
    targetScreenType: KClassifier
): ParsedScreen {
    logger(LogLevel.DEBUG) { "Observing incoming parsed screens and waiting for screen of type $targetScreenType to appear" }
    var cycleCount = 0

    rtNavigationContext.resetDuplicate()

    while (true) {
        if (cycleCount >= rtNavigationContext.maxNumCycleAttempts)
            throw CouldNotFindRTScreenException(targetScreenType)

        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        if (parsedScreen::class == targetScreenType) {
            logger(LogLevel.DEBUG) { "Target screen of type $targetScreenType appeared; cycleCount = $cycleCount" }
            return parsedScreen
        } else {
            logger(LogLevel.VERBOSE) { "Target screen type did not appear yet; cycleCount increased to $cycleCount" }
            cycleCount++
        }
    }
}

/**
 * Adjusts a quantity that is shown currently on screen, using the specified in/decrement buttons.
 *
 * Internally, this first uses a long RT button press to quickly change the quantity
 * to be as close as possible to the [targetQuantity]. Then, with short RT button
 * presses, any leftover differences between the currently shown quantity and
 * [targetQuantity] is corrected.
 *
 * The current quantity is extracted from the current [ParsedScreen] with the
 * [getQuantity] callback. That callback returns null if the quantity currently
 * is not available (typically happens because the screen is blinking). This
 * will not cause an error; instead, this function will just wait until the
 * callback returns a non-null value.
 *
 * Some quantities may be cyclic in nature. For example, a minute value has a valid range
 * of 0-59, but if the current value is 55, and the target value is 3, it is faster to press
 * the [incrementButton] until the value wraps around from 59 to 0 and then keeps increasing
 * to 3. The alternative would be to press the [decrementButton] 52 times, which is slower.
 * This requires a non-null [cyclicQuantityRange] value. If that argument is null, this
 * function will not do such a cyclic logic.
 *
 * Sometimes, it may be beneficial to _not_ long-press the RT button. This is typically
 * the case if the quantity on screen is already very close to [targetQuantity]. In such
 * a case, [longRTButtonPressPredicate] becomes useful. A long RT button press only takes
 * place if [longRTButtonPressPredicate] returns true. Its arguments are [targetQuantity]
 * and the quantity on screen. The default predicate always returns true.
 *
 * [incrementSteps] specifies how the quantity on screen would increment/decrement if the
 * [incrementButton] or [decrementButton] were pressed. This is an array of Pair integers.
 * For each pair, the first integer in the Pair specifies the threshold, the second integer
 * is the step size. Example value: `arrayOf(Pair(0, 10), Pair(100, 50), Pair(1000, 100))`.
 * This means: Values in the 0-100 range are in/decremented by a step size of 10. Values
 * in the 100-1000 range are incremented by a step size of 50. Values at or above 1000
 * are incremented by a step size of 100.
 *
 * NOTE: If [cyclicQuantityRange] is not null, [incrementSteps] must have exactly one item.
 *
 * @param rtNavigationContext Context to use for adjusting the quantity.
 * @param targetQuantity Quantity to set the on-screen quantity to.
 * @param incrementButton What RT button to press for incrementing the on-screen quantity.
 * @param decrementButton What RT button to press for decrementing the on-screen quantity.
 * @param cyclicQuantityRange The cyclic quantity range, or null if no such range exists.
 * @param longRTButtonPressPredicate Quantity delta predicate for enabling RT button presses.
 * @param incrementSteps The step sizes and thresholds the pump uses for in/decrementing.
 *   Must contain at least one item.
 * @param getQuantity Callback for extracting the on-screen quantity.
 * @throws info.nightscout.comboctl.parser.AlertScreenException if alert screens are seen.
 */
suspend fun adjustQuantityOnScreen(
    rtNavigationContext: RTNavigationContext,
    targetQuantity: Int,
    incrementButton: RTNavigationButton = RTNavigationButton.UP,
    decrementButton: RTNavigationButton = RTNavigationButton.DOWN,
    cyclicQuantityRange: Int? = null,
    longRTButtonPressPredicate: (targetQuantity: Int, quantityOnScreen: Int) -> Boolean = { _, _ -> true },
    incrementSteps: Array<Pair<Int, Int>>,
    getQuantity: (parsedScreen: ParsedScreen) -> Int?
) {
    require(incrementSteps.isNotEmpty()) { "There must be at least one incrementSteps item" }
    require((cyclicQuantityRange == null) || (incrementSteps.size == 1)) {
        "If cyclicQuantityRange is not null, incrementSteps must contain " +
        "exactly one item; actually contains ${incrementSteps.size}"
    }

    fun checkIfNeedsToIncrement(currentQuantity: Int): Boolean {
        return if (cyclicQuantityRange != null) {
            val distance = (targetQuantity - currentQuantity)
            if (distance.absoluteValue <= (cyclicQuantityRange / 2))
                (currentQuantity < targetQuantity)
            else
                (currentQuantity > targetQuantity)
        } else
            (currentQuantity < targetQuantity)
    }

    logger(LogLevel.DEBUG) {
        "Adjusting quantity on RT screen; targetQuantity = $targetQuantity; " +
        "increment / decrement buttons = $incrementButton / $decrementButton; " +
        "cyclicQuantityRange = $cyclicQuantityRange"
    }

    var previouslySeenQuantity: Int? = null
    var seenSameQuantityCount = 0

    fun checkIfQuantityUnexpectedlyNotChanging(currentQuantity: Int): Boolean {
        // If the quantity stops changing, and is not the target quantity,
        // something is wrong. Keep observing until MAX_NUM_SAME_QUANTITY_OBSERVATIONS
        // observations are made where the quantity remained unchanged, and then
        // report the situation as bogus (= return false).
        if ((previouslySeenQuantity == null) || (previouslySeenQuantity != currentQuantity)) {
            previouslySeenQuantity = currentQuantity
            seenSameQuantityCount = 0
            return false
        }

        seenSameQuantityCount++

        return (seenSameQuantityCount >= MAX_NUM_SAME_QUANTITY_OBSERVATIONS)
    }

    val initialQuantity: Int
    rtNavigationContext.resetDuplicate()

    // Get the quantity that is initially shown on screen.
    // This is necessary to (a) check if anything needs to
    // be done at all and (b) decide what button to long-press
    // in the code block below.
    while (true) {
        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen
        val quantity = getQuantity(parsedScreen)
        if (quantity != null) {
            initialQuantity = quantity
            break
        }
    }

    logger(LogLevel.DEBUG) { "Initial observed quantity: $initialQuantity" }

    if (initialQuantity == targetQuantity) {
        logger(LogLevel.DEBUG) { "Initial quantity is already the target quantity; nothing to do" }
        return
    }

    val currentQuantity: Int

    if (longRTButtonPressPredicate(targetQuantity, initialQuantity)) {
        val needToIncrement = checkIfNeedsToIncrement(initialQuantity)
        logger(LogLevel.DEBUG) {
            "First phase; long-pressing RT button to " +
                "${if (needToIncrement) "increment" else "decrement"} quantity"
        }

        // First phase: Adjust quantity with a long RT button press.
        // This is (much) faster than using short RT button presses,
        // but can overshoot, especially since the Combo increases the
        // increment/decrement steps over time.
        longPressRTButtonUntil(
            rtNavigationContext,
            if (needToIncrement) incrementButton else decrementButton
        ) { parsedScreen ->
            val currentQuantityOnScreen = getQuantity(parsedScreen)
            logger(LogLevel.VERBOSE) { "Current quantity in first phase: $currentQuantityOnScreen; need to increment: $needToIncrement" }
            if (currentQuantityOnScreen == null) {
                LongPressRTButtonsCommand.ContinuePressingButton
            } else {
                if (currentQuantityOnScreen != targetQuantity) {
                    if (checkIfQuantityUnexpectedlyNotChanging(currentQuantityOnScreen)) {
                        logger(LogLevel.ERROR) { "Quantity unexpectedly not changing" }
                        throw QuantityNotChangingException(targetQuantity = targetQuantity, hitLimitAt = currentQuantityOnScreen)
                    }
                }

                // If we are incrementing, and did not yet reach the
                // quantity, then we expect checkIfNeedsToIncrement()
                // to indicate that further incrementing is required.
                // The opposite is also true: If we are decrementing,
                // and didn't reach the quantity yet, we expect
                // checkIfNeedsToIncrement() to return false. We use
                // this to determine if we need to continue long-pressing
                // the RT button. If the current quantity is at the
                // target, we don't have to anymore. And if we overshot,
                // checkIfNeedsToIncrement() will return the opposite
                // of what we expect. In both of these cases, keepPressing
                // will be set to false, indicating that the long RT
                // button press needs to stop.
                val keepPressing =
                    if (currentQuantityOnScreen == targetQuantity)
                        false
                    else if (needToIncrement)
                        checkIfNeedsToIncrement(currentQuantityOnScreen)
                    else
                        !checkIfNeedsToIncrement(currentQuantityOnScreen)

                if (keepPressing)
                    LongPressRTButtonsCommand.ContinuePressingButton
                else
                    LongPressRTButtonsCommand.ReleaseButton
            }
        }

        var lastQuantity: Int? = null
        var sameQuantityObservedCount = 0
        rtNavigationContext.resetDuplicate()

        // Observe the screens until we see a screen whose quantity
        // is the same as the previous screen's, and we see the quantity
        // not changing 3 times. This "debouncing" is  necessary because
        // the Combo may be somewhat behind with the display frames it
        // sends to the client. This means that even after the
        // longPressRTButtonUntil() call above finished, the Combo may
        // still send several send updates, and the on-screen quantity
        // may still be in/decremented. We need to wait until that
        // in/decrementing is over before we can do any corrections
        // with short RT button presses. And to be sure that it is
        // over, we have to observe the frames for a short while.
        // This also implies that long-pressing the RT button should
        // really only be done if the quantity on screen differs
        // significantly from the target quantity, otherwise the
        // waiting / observation period for this "debouncing" will
        // overshadow any speed gains the long-press may yield.
        // See the longRTButtonPressPredicate documentation.
        while (true) {
            // Do not filter for duplicates, since a duplicate
            // is pretty much what we are waiting for.
            val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = false) ?: continue
            val parsedScreen = parsedDisplayFrame.parsedScreen
            val currentQuantityOnScreen = getQuantity(parsedScreen)

            logger(LogLevel.DEBUG) {
                "Observed quantity after long-pressing RT button: " +
                    "last / current quantity: $lastQuantity / $currentQuantityOnScreen"
            }

            if (currentQuantityOnScreen != null) {
                if (currentQuantityOnScreen == lastQuantity) {
                    sameQuantityObservedCount++
                    if (sameQuantityObservedCount >= 3)
                        break
                } else {
                    lastQuantity = currentQuantityOnScreen
                    sameQuantityObservedCount = 0
                }
            }
        }

        if (lastQuantity == targetQuantity) {
            logger(LogLevel.DEBUG) { "Last seen quantity $lastQuantity is the target quantity; adjustment finished" }
            return
        }

        logger(LogLevel.DEBUG) {
            "Second phase: last seen quantity $lastQuantity is not the target quantity; " +
                "short-pressing RT button(s) to finetune it"
        }

        currentQuantity = lastQuantity!!
    } else {
        while (true) {
            val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
            val parsedScreen = parsedDisplayFrame.parsedScreen
            val quantity = getQuantity(parsedScreen)
            if (quantity != null) {
                currentQuantity = quantity
                break
            }
        }
    }

    // If the on-screen quantity is not the target quantity, we may
    // have overshot, or the in/decrement factor may have been increased
    // over time by the Combo. Perform short RT button presses to nudge
    // the quantity until it reaches the target value. Alternatively,
    // the long RT button press was skipped by request. In that case,
    // we must adjust with short RT button presses.
    val (numNeededShortRTButtonPresses: Int, shortRTButtonToPress) = computeShortRTButtonPress(
        currentQuantity = currentQuantity,
        targetQuantity = targetQuantity,
        cyclicQuantityRange = cyclicQuantityRange,
        incrementSteps = incrementSteps,
        incrementButton = incrementButton,
        decrementButton = decrementButton
    )
    if (numNeededShortRTButtonPresses != 0) {
        logger(LogLevel.DEBUG) {
            "Need to short-press the $shortRTButtonToPress " +
                    "RT button $numNeededShortRTButtonPresses time(s)"
        }
        repeat(numNeededShortRTButtonPresses) {
            // Get display frames. We don't actually do anything with the frame
            // (other than check for a blinked-out screen); this here is done
            // just to avoid missing alert screens while we short-press the button.
            // If an alert screen appears,  getParsedDisplayFrame() throws an
            // AlertScreenException, the caller handles the exception, and if the
            // operation that was being performed before the alert screen appeared
            // can be retried, the caller can attempt to do so.
            while (true) {
                val displayFrame = rtNavigationContext.getParsedDisplayFrame(processAlertScreens = true, filterDuplicates = true)
                if ((displayFrame != null) && displayFrame.parsedScreen.isBlinkedOut) {
                    logger(LogLevel.DEBUG) { "Screen is blinked out (contents: ${displayFrame.parsedScreen}); skipping" }
                    continue
                }
                break
            }
            rtNavigationContext.shortPressButton(shortRTButtonToPress)
        }
    } else {
        logger(LogLevel.DEBUG) {
            "Quantity on screen is already equal to target quantity; no need to press any button"
        }
    }
}

/**
 * Navigates from the current screen to the screen of the given type.
 *
 * This performs a navigation by pressing the appropriate RT buttons to
 * transition between screens until the target screen is reached. This uses
 * an internal navigation tree to compute the shortest path from the current
 * to the target screen. If no path to the target screen can be found,
 * [CouldNotFindRTScreenException] is thrown.
 *
 * Depending on the value of [isComboStopped], the pathfinding algorithm may
 * take different routes, since some screens are only enabled when the pump
 * is running/stopped.
 *
 * @param rtNavigationContext Context to use for navigating.
 * @param targetScreenType Type of the target screen.
 * @param isComboStopped True if the Combo is currently stopped.
 * @return The target screen.
 * @throws CouldNotFindRTScreenException if the screen was not seen even after
 *   this function observed [RTNavigationContext.maxNumCycleAttempts]
 *   screens coming in, or if no path from the current screen to
 *   [targetScreenType] could be found.
 * @throws CouldNotRecognizeAnyRTScreenException if the RT menu is at an
 *   unknown, unrecognized screen at the moment, and in spite of repeatedly
 *   pressing the BACK button to exit back to the main menu, the code
 *   kept seeing unrecognized screens.
 * @throws info.nightscout.comboctl.parser.AlertScreenException if alert screens are seen.
 */
suspend fun navigateToRTScreen(
    rtNavigationContext: RTNavigationContext,
    targetScreenType: KClassifier,
    isComboStopped: Boolean
): ParsedScreen {
    logger(LogLevel.DEBUG) { "About to navigate to RT screen of type $targetScreenType" }

    // Get the current screen to know the starting point. If it is an
    // unrecognized screen, press BACK until we are at the main screen.
    var numAttemptsToRecognizeScreen = 0
    lateinit var currentParsedScreen: ParsedScreen

    rtNavigationContext.resetDuplicate()

    while (true) {
        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        if (parsedScreen is ParsedScreen.UnrecognizedScreen) {
            numAttemptsToRecognizeScreen++
            if (numAttemptsToRecognizeScreen >= rtNavigationContext.maxNumCycleAttempts)
                throw CouldNotRecognizeAnyRTScreenException()
            rtNavigationContext.shortPressButton(RTNavigationButton.BACK)
        } else {
            currentParsedScreen = parsedScreen
            break
        }
    }

    if (currentParsedScreen::class == targetScreenType) {
        logger(LogLevel.DEBUG) { "Already at target; exiting" }
        return currentParsedScreen
    }

    logger(LogLevel.DEBUG) { "Navigation starts at screen of type ${currentParsedScreen::class} and ends at screen of type $targetScreenType" }

    // Figure out the shortest path.
    var path = try {
        findShortestRtPath(currentParsedScreen::class, targetScreenType, isComboStopped)
    } catch (e: IllegalArgumentException) {
        // Happens when currentParsedScreen::class or targetScreenType are not found in the navigation tree.
        null
    }

    if (path?.isEmpty() == true)
        return currentParsedScreen

    if (path == null) {
        // If we reach this place, then the currentParsedScreen was recognized by the parser,
        // but there is no known path in the rtNavigationGraph to get from there to the target.
        // Try exiting by repeatedly pressing BACK. cycleToRTScreen() takes care of that.
        // If it fails to find the main screen, it throws a CouldNotFindRTScreenException.

        logger(LogLevel.WARN) {
            "We are at screen of type ${currentParsedScreen::class}, which is unknown " +
                    "to findRTNavigationPath(); exiting back to the main screen"
        }
        currentParsedScreen = cycleToRTScreen(
            rtNavigationContext,
            RTNavigationButton.BACK,
            ParsedScreen.MainScreen::class
        )

        // Now try again to find a path. We should get a valid path now. We would
        // not be here otherwise, since cycleToRTScreen() throws an exception then.
        path = try {
            findShortestRtPath(currentParsedScreen::class, targetScreenType, isComboStopped)
        } catch (e: IllegalArgumentException) {
            listOf()
        }

        if (path == null) {
            // Should not happen due to the cycleToRTScreen() call above.
            logger(LogLevel.ERROR) { "Could not find RT navigation path even after navigating back to the main menu" }
            throw CouldNotFindRTScreenException(targetScreenType)
        }
    }

    rtNavigationContext.resetDuplicate()

    // Navigate from the current to the target screen.
    var cycleCount = 0
    val pathIt = path.iterator()
    var nextPathItem = pathIt.next()
    var previousScreenType: KClassifier? = null
    while (true) {
        if (cycleCount >= rtNavigationContext.maxNumCycleAttempts)
            throw CouldNotFindRTScreenException(targetScreenType)

        val parsedDisplayFrame = rtNavigationContext.getParsedDisplayFrame(filterDuplicates = true) ?: continue
        val parsedScreen = parsedDisplayFrame.parsedScreen

        // Check if we got the same screen with different content, for example
        // when remaining TBR duration is shown on the main screen and the
        // duration happens to change during this loop. If this occurs,
        // skip the redundant screen.
        if ((parsedScreen::class != ParsedScreen.UnrecognizedScreen::class) &&
            (previousScreenType != null) &&
            (previousScreenType == parsedScreen::class)) {
            logger(LogLevel.DEBUG) { "Got a screen of the same type ${parsedScreen::class}; skipping" }
            continue
        }
        previousScreenType = parsedScreen::class

        // A path item's targetNodeValue is the screen type we are trying
        // to reach, and the edgeValue is the RT button to press to reach it.
        // We stay at the same path item until we reach the screen type that
        // is specified by targetNodeValue. When that happens, we move on
        // to the next path item. Importantly, we _first_ move on to the next
        // item, and _then_ send the short RT button press based on that next
        // item, to avoid sending the RT button from the incorrect path item.
        // Example: Path item 1 contains target screen type A and RT button
        // MENU. Path item 2 contains target screen type B and RT button CHECK.
        // On every iteration, we first check if the current screen is of type
        // A. If it isn't, we need to press MENU again and check in the next
        // iteration again. If it is of type A however, then pressing MENU
        // would be incorrect, since we already are at A. Instead, we _first_
        // must move on to the next path item, and _that_ one says to press
        // CHECK until type B is reached.

        val nextTargetScreenTypeInPath = nextPathItem.targetNodeValue

        logger(LogLevel.DEBUG) { "We are currently at screen $parsedScreen; next target screen type: $nextTargetScreenTypeInPath" }

        if (parsedScreen::class == nextTargetScreenTypeInPath) {
            cycleCount = 0
            if (pathIt.hasNext()) {
                nextPathItem = pathIt.next()
                logger(LogLevel.DEBUG) {
                    "Reached screen type $nextTargetScreenTypeInPath in path; " +
                            "continuing to ${nextPathItem.targetNodeValue}"
                }
            } else {
                // If this is the last path item, it implies
                // that we reached our destination.
                logger(LogLevel.DEBUG) { "Target screen type $targetScreenType reached" }
                return parsedScreen
            }
        }

        val navButtonToPress = nextPathItem.edgeValue.button
        logger(LogLevel.DEBUG) { "Pressing button $navButtonToPress to navigate further" }
        rtNavigationContext.shortPressButton(navButtonToPress)

        cycleCount++
    }
}

internal fun findShortestRtPath(from: KClassifier, to: KClassifier, isComboStopped: Boolean) =
    rtNavigationGraph.findShortestPath(from, to) {
        when (it.edgeValidityCondition) {
            RTEdgeValue.EdgeValidityCondition.ALWAYS -> true
            RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_RUNNING -> !isComboStopped
            RTEdgeValue.EdgeValidityCondition.ONLY_IF_COMBO_STOPPED -> isComboStopped
        }
    }

internal fun computeShortRTButtonPress(
    currentQuantity: Int,
    targetQuantity: Int,
    cyclicQuantityRange: Int?,
    incrementSteps: Array<Pair<Int, Int>>,
    incrementButton: RTNavigationButton,
    decrementButton: RTNavigationButton
): Pair<Int, RTNavigationButton> {
    val numNeededShortRTButtonPresses: Int
    val shortRTButtonToPress: RTNavigationButton

    // Compute the number of RT button press steps to cover the given distance.
    // Use the (x + (d-1)) / d formula (with integer division) to round up the
    // result. That's because in case of "half steps", these must be counted as
    // one full step. For example, if the current quantity on screen is 21, the
    // target quantity is 40, and the step size is 20, then pressing UP will
    // cause the Combo to increment the quantity from 21 to 40. A further UP
    // button press would then increment from 40 to 60 etc. If we didn't round
    // up, the "half-step" would not be counted. In the example above, this
    // would compute 0, since (40-21)/20 = 19/20 = 0 (integer division). The
    // rounding formula by contrast: (40-21+(20-1))/20 = (19+19)/20 = 38/20 = 1.
    fun computeNumSteps(stepSize: Int, distance: Int) = (distance + (stepSize - 1)) / stepSize

    if (currentQuantity == targetQuantity) {
        numNeededShortRTButtonPresses = 0
        shortRTButtonToPress = RTNavigationButton.CHECK
    } else if (incrementSteps.size == 1) {
        val stepSize = incrementSteps[0].second
        require(stepSize > 0)
        val distance = (targetQuantity - currentQuantity).absoluteValue
        if (cyclicQuantityRange != null) {
            // With a cyclic quantity, if the absolute distance between
            // quantities exceeds half of that range, we have the option
            // to change the quantity in the opposite direction which
            // requires fewer button presses. For example, if the range
            // is 60, and the absolute distance is 40, we'd normally have
            // to press a button 40 times to get to the target quantity.
            // But since cyclic quantities wrap around, we can instead
            // press the opposite button 60-40 = 20 times to also get
            // to the target quantity.
            if (distance > (cyclicQuantityRange / 2)) {
                numNeededShortRTButtonPresses = computeNumSteps(stepSize, cyclicQuantityRange - distance)
                shortRTButtonToPress = if (targetQuantity < currentQuantity) incrementButton else decrementButton
            } else {
                numNeededShortRTButtonPresses = computeNumSteps(stepSize, distance)
                shortRTButtonToPress = if (targetQuantity > currentQuantity) incrementButton else decrementButton
            }
        } else {
            numNeededShortRTButtonPresses = computeNumSteps(stepSize, distance)
            shortRTButtonToPress = if (targetQuantity > currentQuantity) incrementButton else decrementButton
        }
    } else {
        val (start, end, button) = if (currentQuantity < targetQuantity)
            Triple(currentQuantity, targetQuantity, incrementButton)
        else
            Triple(targetQuantity, currentQuantity, decrementButton)

        shortRTButtonToPress = button

        var currentValue = start
        var numPresses = 0

        for (index in incrementSteps.indices) {
            val incrementStep = incrementSteps[index]
            val stepSize = incrementStep.second
            require(stepSize > 0)
            val curRangeStart = incrementStep.first
            val curRangeEnd = if (index == incrementSteps.size - 1)
                end
            else
                min(incrementSteps[index + 1].first, end)

            if (currentValue >= curRangeEnd)
                continue

            if (currentValue < curRangeStart)
                currentValue = curRangeStart

            numPresses += computeNumSteps(stepSize, curRangeEnd - currentValue)

            currentValue = curRangeEnd

            if (currentValue >= end)
                break
        }

        numNeededShortRTButtonPresses = numPresses
    }

    return Pair(numNeededShortRTButtonPresses, shortRTButtonToPress)
}
