package info.nightscout.comboctl.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Interface for Combo IO operations.
 *
 * The send and receive functions are suspending functions to be
 * able to fo pairing and regular sessions by using coroutines.
 * Subclasses concern themselves with adapting blocking IO APIs
 * and framing the data in some way. Subclasses can also choose
 * to use Flows, Channels, and RxJava/RxKotlin mechanisms
 * if they wish.
 *
 * IO errors in subclasses are communicated to callers by
 * throwing exceptions.
 */
interface ComboIO {
    /**
     * Sends the given block of bytes, suspending the coroutine until it is done.
     *
     * This function either transmits all of the bytes, or throws an
     * exception if this fails. Partial transmissions are not done.
     * An exception is also thrown if sending fails due to something
     * that's not an error, like when a connection is closed.
     *
     * If an exception is thrown, the data is to be considered not
     * having been sent.
     *
     * @param dataToSend The data to send. Must not be empty.
     * @throws CancellationException if sending is aboted due to
     *         a terminated IO, typically due to some sort of
     *         disconnect function call. (This is _not_ thrown if
     *         the remote side closes the connection! In such a
     *         case, ComboIOException is thrown instead.)
     * @throws ComboIOException if sending fails.
     * @throws IllegalStateException if this object is in a state
     *         that does not permit sending, such as a device
     *         that has been shut down or isn't connected.
     */
    suspend fun send(dataToSend: List<Byte>)

    /**
     * Receives a block of bytes, suspending the coroutine until it finishes.
     *
     * If receiving fails, an exception is thrown. An exception
     * is also thrown if receiving fails due to something that's not
     * an error, like when a connection is closed.
     *
     * @return Received block of bytes. This is never empty.
     * @throws CancellationException if receiving is aboted due to
     *         a terminated IO, typically due to some sort of
     *         disconnect function call. (This is _not_ thrown if
     *         the remote side closes the connection! In such a
     *         case, ComboIOException is thrown instead.)
     * @throws ComboIOException if receiving fails.
     * @throws IllegalStateException if this object is in a state
     *         that does not permit receiving, such as a device
     *         that has been shut down or isn't connected.
     */
    suspend fun receive(): List<Byte>
}

/**
 * Abstract combo IO class for adapting blocking IO APIs.
 *
 * The implementations of the ComboIO interface send and receive
 * calls internally use blocking send/receive functions and run
 * them in the IO context to make sure their blocking behavior
 * does not block the coroutine. Subclasses must implement
 * blockingSend and blockingReceive.
 *
 * @property ioDispatcher [CoroutineDispatcher] where the
 *   [blockingSend] and [blockingReceive] calls shall take place.
 *   These calls may block threads for a nontrivial amount of time,
 *   so the dispatcher must be suitable for that. On JVM and Android
 *   platforms, there is an IO dispatcher for this.
 */
abstract class BlockingComboIO(val ioDispatcher: CoroutineDispatcher) : ComboIO {
    final override suspend fun send(dataToSend: List<Byte>) {
        withContext(ioDispatcher) {
            blockingSend(dataToSend)
        }
    }

    final override suspend fun receive(): List<Byte> {
        return withContext(ioDispatcher) {
            blockingReceive()
        }
    }

    /**
     * Blocks the calling thread until the given block of bytes is fully sent.
     *
     * In case of an error, or some other reason why sending
     * cannot be done (like a closed connection), an exception
     * is thrown.
     *
     * This function sends atomically. Either, the entire data
     * is sent, or none of it is sent (the latter happens in
     * case of an exception).
     *
     * @param dataToSend The data to send. Must not be empty.
     * @throws CancellationException if sending is aboted due to
     *         a terminated IO, typically due to some sort of
     *         disconnect function call. (This is _not_ thrown if
     *         the remote side closes the connection! In such a
     *         case, ComboIOException is thrown instead.)
     * @throws ComboIOException if sending fails.
     * @throws IllegalStateException if this object is in a state
     *         that does not permit sending, such as a device
     *         that has been shut down or isn't connected.
     */
    abstract fun blockingSend(dataToSend: List<Byte>)

    /**
     * Blocks the calling thread until a given block of bytes is received.
     *
     * In case of an error, or some other reason why receiving
     * cannot be done (like a closed connection), an exception
     * is thrown.
     *
     * @return Received block of bytes. This is never empty.
     *
     * @throws CancellationException if receiving is aboted due to
     *         a terminated IO, typically due to some sort of
     *         disconnect function call. (This is _not_ thrown if
     *         the remote side closes the connection! In such a
     *         case, ComboIOException is thrown instead.)
     * @throws ComboIOException if receiving fails.
     * @throws IllegalStateException if this object is in a state
     *         that does not permit receiving, such as a device
     *         that has been shut down or isn't connected.
     */
    abstract fun blockingReceive(): List<Byte>
}

/**
 * ComboIO subclass that puts data into Combo frames and uses
 * another ComboIO object for the actual transmission.
 *
 * This is intended to be used for composing framed IO with
 * another ComboIO subclass. This allows for easily adding
 * Combo framing without having to modify ComboIO subclasses
 * or having to manually integrate the Combo frame parser.
 *
 * @property io Underlying ComboIO to use for sending
 *           and receiving&parsing framed data.
 */
class FramedComboIO(private val io: ComboIO) : ComboIO {
    override suspend fun send(dataToSend: List<Byte>) = io.send(dataToSend.toComboFrame())

    override suspend fun receive(): List<Byte> {
        try {
            // Loop until a full frame is parsed, an
            // error occurs, or the job is canceled.
            // In the latter two cases, an exception
            // is thrown, so we won't end up in an
            // infinite loop here.
            while (true) {
                val parseResult = frameParser.parseFrame()
                if (parseResult == null) {
                    frameParser.pushData(io.receive())
                    continue
                }

                return parseResult
            }
        } catch (e: CancellationException) {
            frameParser.reset()
            throw e
        } catch (e: ComboIOException) {
            frameParser.reset()
            throw e
        }
    }

    /**
     * Resets the internal frame parser.
     *
     * Resetting means that any partial frame data inside
     * the parse is discarded. This is useful if this IO
     * object is reused.
     */
    fun reset() {
        frameParser.reset()
    }

    private val frameParser = ComboFrameParser()
}

/**
 * Base class for exceptions related to IO operations from/to the Combo.
 *
 * @param message The detail message.
 * @param cause Throwable that further describes the cause of the exception.
 */
open class ComboIOException(message: String?, cause: Throwable?) : ComboException(message, cause) {
    constructor(message: String) : this(message, null)
    constructor(cause: Throwable) : this(null, cause)
}
