package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.parser.AlertScreenException
import info.nightscout.comboctl.parser.ParsedScreen
import info.nightscout.comboctl.parser.parseDisplayFrame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private val logger = Logger.get("ParsedDisplayFrameStream")

/**
 * Combination of a [DisplayFrame] and the [ParsedScreen] that is the result of parsing that frame.
 */
data class ParsedDisplayFrame(val displayFrame: DisplayFrame, val parsedScreen: ParsedScreen)

/**
 * Class for parsing and processing a stream of incoming [DisplayFrame] data.
 *
 * This takes incoming [DisplayFrame] data through [feedDisplayFrame], parses these
 * frames, and stores the frame along with its [ParsedScreen]. Consumers can get
 * the result with [getParsedDisplayFrame]. If [feedDisplayFrame] is called before
 * a previously parsed frame is retrieved, the previous un-retrieved copy gets
 * overwritten. If no parsed frame is currently available, [getParsedDisplayFrame]
 * suspends the calling coroutine until a parsed frame becomes available.
 *
 * [getParsedDisplayFrame] can also detect duplicate screens by comparing the
 * last and current frame's [ParsedScreen] parsing results. In other words, duplicates
 * are detected by comparing the parsed contents, not the frame pixels (unless both
 * frames could not be parsed).
 *
 * [resetAll] resets all internal states and recreates the internal [Channel] that
 * stores the last parsed frame. [resetDuplicate] resets the states associated with
 * detecting duplicate screens.
 *
 * The [flow] offers a [SharedFlow] of the parsed display frames. This is useful
 * for showing the frames on a GUI for example.
 *
 * During operation, [feedDisplayFrame], and [getParsedDisplayFrame]
 * can be called concurrently, as can [resetAll] and [getParsedDisplayFrame].
 * Other functions and call combinations lead to undefined behavior.
 *
 * This "stream" class is used instead of a more common Kotlin coroutine flow
 * because the latter do not fit well in the whole Combo RT display dataflow
 * model, where the ComboCtl code *pulls* parsed frames. Flows - specifically
 * SharedFlows - are instead more suitable for *pushing* frames. Also, this
 * class helps with diagnostics and debugging since it stores the actual
 * frames along with their parsed screen counterparts, and there are no caches
 * in between the display frames and the parsed screens which could lead to
 * RT navigation errors due to the parsed screens indicating something that
 * is not actually the current state and rather a past state instead.
 */
class ParsedDisplayFrameStream {
    private val _flow = MutableSharedFlow<ParsedDisplayFrame?>(onBufferOverflow = BufferOverflow.DROP_OLDEST, replay = 1)
    private var parsedDisplayFrameChannel = createChannel()
    private var lastRetrievedParsedDisplayFrame: ParsedDisplayFrame? = null

    /**
     * [SharedFlow] publishing all incoming and newly parsed frames.
     *
     * This if [feedDisplayFrame] is called with a null argument.
     */
    val flow: SharedFlow<ParsedDisplayFrame?> = _flow.asSharedFlow()

    /**
     * Resets all internal states back to the initial conditions.
     *
     * The [flow]'s replay cache is reset by this as well. This also
     * resets all duplicate detection related states, so calling
     * [resetDuplicate] after this is redundant.
     *
     * This aborts an ongoing suspending [getParsedDisplayFrame] call.
     */
    fun resetAll() {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        _flow.resetReplayCache()
        parsedDisplayFrameChannel.close()
        parsedDisplayFrameChannel = createChannel()
        lastRetrievedParsedDisplayFrame = null
    }

    /**
     * Sets the internal states to reflect a given error.
     *
     * This behaves similar to [resetAll]. However, the internal Channel
     * for parsed display frames is closed with the specified [cause], and
     * is _not_ reopened afterwards. [resetAll] has to be called after
     * this function to be able to use the [ParsedDisplayFrameStream] again.
     * This is intentional; it makes sure any attempts at getting parsed
     * display frames etc. fail until the user explicitly resets this stream.
     *
     * @param cause The throwable that caused the error. Any currently
     *   suspended [getParsedDisplayFrame] call will be aborted and this
     *   cause will be thrown from that function.
     */
    fun abortDueToError(cause: Throwable?) {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        _flow.resetReplayCache()
        parsedDisplayFrameChannel.close(cause)
    }

    /**
     * Resets the states that are associated with duplicate screen detection.
     *
     * See [getParsedDisplayFrame] for details about duplicate screen detection.
     */
    fun resetDuplicate() {
        lastRetrievedParsedDisplayFrame = null
    }

    /**
     * Feeds a new [DisplayFrame] into this stream, parses it, and stores the parsed frame.
     *
     * The parsed frame is stored as a [ParsedDisplayFrame] instance. If there is already
     * such an instance stored, that previous one is overwritten. This also publishes
     * the new [ParsedDisplayFrame] instance through [flow]. This can also stored a null
     * reference to signal that frames are currently unavailable.
     *
     * [resetAll] erases the stored frame.
     *
     * This and [getParsedDisplayFrame] can be called concurrently.
     */
    fun feedDisplayFrame(displayFrame: DisplayFrame?) {
        val newParsedDisplayFrame = displayFrame?.let {
            ParsedDisplayFrame(it, parseDisplayFrame(it))
        }

        parsedDisplayFrameChannel.trySend(newParsedDisplayFrame)
        _flow.tryEmit(newParsedDisplayFrame)
    }

    /**
     * Returns true if a frame has already been stored by a [feedDisplayFrame] call.
     *
     * [getParsedDisplayFrame] retrieves a stored frame, so after such a call, this
     * would return false again until a new frame is stored with [feedDisplayFrame].
     *
     * This is not thread safe; it is not safe to call this and [feedDisplayFrame] /
     * [getParsedDisplayFrame] simultaneously.
     */
    fun hasStoredDisplayFrame(): Boolean =
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        !(parsedDisplayFrameChannel.isEmpty)

    /**
     * Retrieves the last [ParsedDisplayFrame] that was stored by [feedDisplayFrame].
     *
     * If no such frame was stored, this suspends until one is stored or [resetAll]
     * is called. In the latter case, [ClosedReceiveChannelException] is thrown.
     *
     * If [filterDuplicates] is set to true, this function compares the current
     * parsed frame with the last. If the currently stored frame is found to be
     * equal to the last one, it is considered a duplicate, and gets silently
     * dropped. This function then waits for a new frame, suspending the coroutine
     * until [feedDisplayFrame] is called with a new frame.
     *
     * In some cases, the last frame that is stored in this class for purposes
     * of duplicate detection is not valid anymore and will lead to incorrect
     * duplicate detection behavior. In such cases, [resetDuplicate] can be called.
     * This erases the internal last frame.
     *
     * [processAlertScreens] specifies whether this function should pre-check the
     * contents of the [ParsedScreen]. If set to true, it will see if the parsed
     * screen is a [ParsedScreen.AlertScreen]. If so, it extracts the contents of
     * the alert screen and throws an [AlertScreenException]. If instead
     * [processAlertScreens] is set to false, alert screens are treated just like
     * any other ones.
     *
     * @return The last frame stored by [feedDisplayFrame].
     * @throws ClosedReceiveChannelException if [resetAll] is called while this
     *   suspends the coroutine and waits for a new frame.
     * @throws AlertScreenException if [processAlertScreens] is set to true and
     *   an alert screen is detected.
     * @throws PacketReceiverException thrown when the [TransportLayer.IO] packet
     *   receiver loop failed due to an exception. Said exception is wrapped in
     *   a PacketReceiverException and forwarded all the way to this function
     *   call, which will keep throwing that cause until [resetAll] is called
     *   to reset the internal states.
     */
    suspend fun getParsedDisplayFrame(filterDuplicates: Boolean = false, processAlertScreens: Boolean = true): ParsedDisplayFrame? {
        while (true) {
            val thisParsedDisplayFrame = parsedDisplayFrameChannel.receive()
            val lastParsedDisplayFrame = lastRetrievedParsedDisplayFrame

            if (filterDuplicates && (lastParsedDisplayFrame != null) && (thisParsedDisplayFrame != null)) {
                val lastParsedScreen = lastParsedDisplayFrame.parsedScreen
                val thisParsedScreen = thisParsedDisplayFrame.parsedScreen
                val lastDisplayFrame = lastParsedDisplayFrame.displayFrame
                val thisDisplayFrame = thisParsedDisplayFrame.displayFrame

                // If both last and current screen could not be parsed, we can't compare
                // any parsed contents. Resort to comparing pixels in that case instead.
                // Normally though we compare contents, since this is faster, and sometimes,
                // the pixels change but the contents don't (example: a frame showing the
                // time with a blinking ":" character).
                val isDuplicate = if ((lastParsedScreen is ParsedScreen.UnrecognizedScreen) && (thisParsedScreen is ParsedScreen.UnrecognizedScreen))
                    (lastDisplayFrame == thisDisplayFrame)
                else
                    (lastParsedScreen == thisParsedScreen)

                if (isDuplicate)
                    continue
            }

            lastRetrievedParsedDisplayFrame = thisParsedDisplayFrame

            // Blinked-out screens are unusable; skip them, otherwise
            // they may mess up RT navigation.
            if ((thisParsedDisplayFrame != null) && thisParsedDisplayFrame.parsedScreen.isBlinkedOut) {
                logger(LogLevel.DEBUG) { "Screen is blinked out (contents: ${thisParsedDisplayFrame.parsedScreen}); skipping" }
                continue
            }

            if (processAlertScreens && (thisParsedDisplayFrame != null)) {
                if (thisParsedDisplayFrame.parsedScreen is ParsedScreen.AlertScreen)
                    throw AlertScreenException(thisParsedDisplayFrame.parsedScreen.content)
            }

            return thisParsedDisplayFrame
        }
    }

    private fun createChannel() =
        Channel<ParsedDisplayFrame?>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
