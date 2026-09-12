package app.aaps.wear.complications.cwf

import app.aaps.wear.complications.ComplicationAction
import app.aaps.wear.complications.ComplicationTapActivity
import app.aaps.wear.complications.ModernBaseComplicationProviderService

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.shared.impl.weardata.ResFileMap
import app.aaps.wear.R
import app.aaps.wear.watchfaces.CustomWatchface
import dev.zacsweers.metro.HasMemberInjections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import app.aaps.wear.data.ComplicationData as ComplicationStore

/**
 * Publishes the Custom watch face as one image complication.
 *
 * This is the bridge for watches whose firmware no longer selects or binds code-based watch faces
 * (Galaxy Watch 7 and later). There a Watch Face Format document is the only face that can run, and
 * a complication is the only channel from AAPS into it. So the CWF keeps being drawn by our own
 * code - honouring the user's zip, its dynamic values and its `dynPref` cascade - and the result is
 * handed over as a picture for the document to display.
 *
 * It used to be two providers, a slow half and a clock-carrying half, so each could refresh at its
 * own rate. Measuring that on a Galaxy Watch 4 showed the split was not where the cost was: blending
 * a cached layer costs about 1 ms, so splitting the work across two complications bought nothing
 * while costing two data reads, two compressions, and the need to keep the halves agreeing with each
 * other. The layers now live inside [CwfFramePipeline], which cuts the face wherever the refresh rate
 * changes and blends them back in paint order - see `_docs/CWF_WFF_Prompt.md` sections 10.4 to 10.6.
 *
 * Most requests are answered from [CwfFrameQueue] without drawing anything, because the time is the
 * one value known in advance and the coming seconds can be prepared while the watch is idle.
 *
 * Limits inherent to the approach, documented there rather than worked around here: the picture only
 * changes when the system asks for an update - and under load it stops asking, which no amount of
 * preparation can fix - and the user's own complication slots are not part of it, because
 * complication data only ever reaches the watch face the system has bound.
 */
@HasMemberInjections
open class CwfFaceComplication : ModernBaseComplicationProviderService() {

    companion object {

        /**
         * The watch face, kept for the life of the process rather than of a service instance: the
         * system binds a data source only for as long as it takes to answer, so an instance field
         * would be discarded between requests and every render would pay full construction again -
         * about 275 ms against about 90 ms warm, since construction, injection and inflation
         * dominate.
         *
         * Holding it is safe: `prepareForRendering` attaches the **application** context, so nothing
         * belonging to a single bind is retained.
         */
        private var warmWatchFace: CustomWatchface? = null

        /** How long the preview may wait on storage before falling back to the built-in image. */
        private const val PREVIEW_READ_TIMEOUT_MS = 500L

        /**
         * How long the last frame took, render plus encode, in milliseconds.
         *
         * Published for [CwfComplicationUpdater], which paces itself by it. Measured on a Galaxy
         * Watch 4: the upper half takes 576 ms to 2.2 s there, against 34 ms on an emulator, so a
         * fixed one second tick asks for far more than the watch can deliver, the requests queue,
         * and the backlog then flushes as several frames at once - seen as a second hand jumping
         * 30 s, then 2 s, then 5 s.
         */
        internal val lastFrameMs: Long get() = framePipeline?.lastFrameMs ?: 0

        /** Frames prepared ahead of the moment they are needed. Built with the first request. */
        private var frameQueue: CwfFrameQueue? = null

        /** Layer caches, assembly and compression. Built on first use, when a context exists. */
        private var framePipeline: CwfFramePipeline? = null

        /** The face without seconds, published by the always-on slot - see [CwfMinuteFrame]. */
        internal val minuteFrame = CwfMinuteFrame()

        /**
         * Held while the secondless picture is being drawn, so it is drawn once and not twice.
         *
         * Both slots want it, and at the minute boundary both are asked within a few milliseconds of
         * each other. Without this they each found nothing held, each drew the same picture, and the
         * watch paid two compressions a minute instead of one - measured on the emulator, 7 ms apart.
         */
        private val minuteFrameLock = Mutex()

        /**
         * Identifies the generation of data the prepared frames were built from.
         *
         * Bumped whenever the picture's content changes for a reason other than the clock, which
         * makes every prepared frame wrong at once. A wrong frame shown for even one second is worse
         * than a late one, so the queue is emptied rather than mixed.
         */
        private val dataToken = AtomicInteger(0)

        internal fun currentSecond(): Long = System.currentTimeMillis() / 1_000 * 1_000

        /**
         * The second a frame requested now will actually be *showing* by the time it appears.
         *
         * A frame drawn for the second it starts in is already stale when it lands: producing one
         * takes 160 to 750 ms depending on how warm the watch is. Measured on a Galaxy Watch 4, a
         * frame begun at 54.92 depicted second 54 and appeared at 55.117 - so the wearer saw 54 when
         * it was already 55, and the next frame showed 56. One second late reads as a two second jump.
         *
         * So the frame is drawn for the second it will land in: now, plus what the last frame cost,
         * plus a small bias. The bias makes ties round **up**, because the two errors are not equal -
         * showing the next second a few tens of milliseconds early is invisible, while showing the
         * previous one is the fault above. This is the wearer's own rule, and the arithmetic leaves
         * the case that already worked untouched: where the second had not turned yet, it still
         * returns the same answer.
         *
         * And it never goes backwards. What a frame costs varies from 200 ms to over a second on a
         * busy watch, so two frames drawn moments apart could aim at decreasing instants - seen on a
         * Galaxy Watch 4 as the minute hand stepping back and then forward again. Time only moves one
         * way, so clamping to the last instant drawn can never hold a frame back.
         */
        internal fun landingSecond(): Long {
            val next = landingSecondAt(System.currentTimeMillis(), lastFrameMs, lastLandingSecond)
            lastLandingSecond = next
            return next
        }

        /** The instant most recently drawn for, so the next one can never be earlier. */
        @Volatile private var lastLandingSecond = 0L

        /** The second of the frame served before this one, so a break in the series can be noticed. */
        @Volatile private var lastServedSecond = 0L

        /**
         * Records the instant a frame actually depicts, whoever produced it.
         *
         * Both paths must report: a frame built here already aims at [landingSecond], but one served
         * from the queue carries its own second, and only the queue knows what that is.
         */
        internal fun noteDrawn(second: Long) {
            if (second > lastLandingSecond) lastLandingSecond = second
        }

        /**
         * The same rule as arithmetic, so it can be checked without a watch: the second that will be
         * current [productionMs] from [now], rounded up by [LANDING_BIAS_MS] so a tie lands on the
         * next second rather than the one just gone.
         */
        internal fun landingSecondAt(now: Long, productionMs: Long, lastDrawn: Long = 0L): Long =
            maxOf((now + productionMs + LANDING_BIAS_MS) / 1_000 * 1_000, lastDrawn)

        /** Rounds a frame towards the second after it rather than the one before. */
        private const val LANDING_BIAS_MS = 150L

        internal fun pipeline(context: Context, aapsLogger: AAPSLogger): CwfFramePipeline =
            framePipeline ?: CwfFramePipeline(
                context = context.applicationContext,
                aapsLogger = aapsLogger,
                watchFaceProvider = {
                    warmWatchFace ?: CustomWatchface().also {
                        it.prepareForRendering(context)
                        warmWatchFace = it
                    }
                }
            ).also { framePipeline = it }

        internal fun queue(aapsLogger: AAPSLogger): CwfFrameQueue =
            frameQueue ?: CwfFrameQueue(aapsLogger).also { frameQueue = it }

        /**
         * Marks every prepared frame stale, because the data or the layout changed.
         *
         * Called by [CwfComplicationUpdater] on the same events that used to force a full redraw.
         */
        internal fun invalidate(everything: Boolean) {
            dataToken.incrementAndGet()
            framePipeline?.let { if (everything) it.invalidateAll() else it.invalidateData() }
            frameQueue?.clear()
            // It is a picture too, and new data makes it as wrong as any other
            minuteFrame.clear()
        }

        /**
         * Drops the prepared frames because the mode changed, and **keeps the cached layers**.
         *
         * A wrist raise changes how the face should look, not what it shows: the glucose, the chart
         * and every value are the same as a moment ago. Treating it as a data change threw the
         * interactive layers away and made the first frame after waking rebuild all of them.
         *
         * The prepared frames do have to go: each was drawn for the mode it was built in, and an
         * ambient frame has no second hand.
         */
        internal fun modeChanged() {
            dataToken.incrementAndGet()
            frameQueue?.clear()
        }

        /** The generation the prepared frames belong to. */
        internal fun token(): Int = dataToken.get()

        /** Which design the views currently carry, so a caller can tell whether a new zip took effect. */
        internal fun currentStyleId(): Int = warmWatchFace?.styleId ?: -1

        /** How many finished frames are waiting. Zero means a request has to draw one itself. */
        internal fun preparedFrames(): Int = frameQueue?.size ?: 0

        /**
         * Builds one frame that is not prepared yet, if any is missing.
         *
         * Called from a loop while the watch is awake and showing seconds, so the queue keeps a few
         * seconds ready ahead of the requests. Returns false when there is nothing left to build,
         * which lets the caller wait instead of spinning.
         *
         * Runs on the main thread because it draws a view hierarchy, and the same thread must own
         * those views every time.
         */
        internal suspend fun prepareAhead(context: Context, aapsLogger: AAPSLogger): Boolean =
            withContext(Dispatchers.Main.immediate) {
                val ambient = isAmbient(context)
                val token = token()
                val queue = queue(aapsLogger)
                val metrics = context.resources.displayMetrics
                val pipeline = pipeline(context, aapsLogger)
                // The face without seconds, for the always-on slot. Refreshed only when the minute
                // turns: nothing else about it moves that the eye can see, since the minute hand
                // travels 0.1 degree per second. One compression a minute, and both slots share it.
                val minuteFor = System.currentTimeMillis()
                if (minuteFrame.stale(minuteFor)) {
                    val plain = pipeline.compose(minuteFor, ambient, metrics.widthPixels, metrics.heightPixels) { true }
                    minuteFrame.offer(minuteFor, withContext(Dispatchers.Default) { pipeline.encode(plain) })
                }
                // Nothing else is worth preparing while the watch dozes: the awake slot is drawn at
                // alpha 0 there, whatever the wearer chose, so a queue of frames carrying seconds
                // would be work nobody could see. This is what always-on used to spend a full frame
                // every five to thirteen seconds on.
                // Nor when the face has no seconds to show: every frame in the queue would be the
                // picture that is already held.
                if (ambient || !showsSeconds()) return@withContext true
                queue.reset(token, ambient)
                // One second ahead: the second in progress is either already served or about to be,
                // and preparing it would be work for a moment that has passed by the time it lands
                val next = queue.missing(currentSecond() + 1_000).firstOrNull() ?: return@withContext false
                val bitmap = pipeline.compose(next, ambient, metrics.widthPixels, metrics.heightPixels) { isAmbient(context) }
                // Compression is the dearest step and only reads pixels, so it leaves the main thread
                // - which also lets a request be answered while a prepared frame is still compressing
                val bytes = withContext(Dispatchers.Default) { pipeline.encode(bitmap) }
                queue.offer(next, token, ambient, bytes)
                true
            }

        /**
         * Whether the face currently being drawn shows seconds at all.
         *
         * Both the zip and the user have a say - `enableSecond` is the json's `enableSecond` **and**
         * the "show seconds" preference - so this cannot be answered from the preference alone. Used
         * to decide how often the upper half is worth refreshing: a design without seconds gains
         * nothing from a per-second render and would only cost battery.
         *
         * False until something has been drawn, which errs towards the cheap rate.
         */
        internal fun showsSeconds(): Boolean = warmWatchFace?.enableSecond == true

        /**
         * True when the watch is in its low-power always-on state rather than active.
         *
         * A complication data source is never told about ambient mode - nothing in the request says
         * so - but it can look at the display, which reports a doze state while the watch is in
         * ambient. That is what lets the picture hide its seconds and drop to the cheap refresh rate
         * without the watch face having to tell us.
         */
        internal fun isAmbient(context: Context): Boolean =
            (context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.state
                ?: Display.STATE_ON) != Display.STATE_ON
    }

    /** Runs the request. Main by default because [render] must own the view hierarchy. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Label shown in the complication picker and used as the content description. */
    protected open val label: Int = R.string.label_watchface_custom

    /**
     * Whether this provider publishes the face **without** seconds.
     *
     * The pushed document has one slot for each and swaps them itself when the watch dozes - see
     * [CwfMinuteFrame] for why that is the only thing that wins the race. Both providers draw from
     * the same shared production, so the second slot costs one compression a minute, not a second
     * face.
     */
    protected open val withoutSeconds: Boolean = false

    override fun buildComplicationData(
        type: ComplicationType,
        data: ComplicationStore,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? = buildFromIcon(type, iconFor(frameBlocking()), complicationPendingIntent)

    /**
     * Builds a frame on the calling thread.
     *
     * Nothing reaches this today - [onComplicationRequest] and `getPreviewData` are both overridden -
     * but the base class offers three other routes to [buildComplicationData], and returning null from
     * them would leave the face empty with nothing in the log to say why. That failure has happened
     * before on hardware we cannot test on, so this path draws a real picture instead.
     */
    private fun frameBlocking(): ByteArray {
        val metrics = resources.displayMetrics
        val pipeline = pipeline(this, aapsLogger)
        return pipeline.encode(pipeline.compose(currentSecond(), isAmbient(this), metrics.widthPixels, metrics.heightPixels))
    }

    /**
     * Draws the face without seconds, and keeps it for whoever asks next.
     *
     * Only reached when nothing is held - the producer normally has one ready. Composed with the mode
     * the watch is in and the seconds simply left out, never by switching the render to always-on:
     * the mode also chooses `simpleUi`, and switching it here would draw an empty picture for anyone
     * using the simple always-on display.
     */
    private suspend fun buildMinuteFrame(): ByteArray = minuteFrameLock.withLock {
        // Asked again inside the lock: while this waited, the other slot may have drawn exactly this
        // picture, and drawing it twice is a compression wasted
        minuteFrame.take(System.currentTimeMillis())?.let { return@withLock it.bytes }
        val metrics = resources.displayMetrics
        val pipeline = pipeline(this, aapsLogger)
        val now = System.currentTimeMillis()
        val bitmap = pipeline.compose(now, isAmbient(this), metrics.widthPixels, metrics.heightPixels) { true }
        val bytes = withContext(Dispatchers.Default) { pipeline.encode(bitmap) }
        minuteFrame.offer(now, bytes)
        aapsLogger.debug(LTag.WEAR, "${javaClass.simpleName}: built the minute frame, ${bytes.size / 1024} kB")
        bytes
    }

    /** Wraps an already rendered frame in the complication type the slot asked for. */
    private fun buildFromIcon(type: ComplicationType, icon: Icon, tapIntent: PendingIntent?): ComplicationData? {
        val contentDescription = PlainComplicationText.Builder(text = getString(label)).build()

        return when (type) {
            ComplicationType.PHOTO_IMAGE -> PhotoImageComplicationData.Builder(
                photoImage = icon,
                contentDescription = contentDescription
            )
                .apply { tapIntent?.let { setTapAction(it) } }
                .build()

            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                smallImage = SmallImage.Builder(image = icon, type = SmallImageType.PHOTO).build(),
                contentDescription = contentDescription
            )
                .apply { tapIntent?.let { setTapAction(it) } }
                .build()

            else                         -> {
                aapsLogger.warn(LTag.WEAR, "${javaClass.simpleName} unexpected type: $type")
                null
            }
        }
    }

    /** Wraps finished frame bytes so a complication can carry them. */
    private fun iconFor(bytes: ByteArray): Icon = Icon.createWithData(bytes, 0, bytes.size)

    /**
     * Answers a request from the queue when a frame is ready, and builds one when it is not.
     *
     * The whole point of preparing frames is that this path usually does no work at all. When it does
     * have to build one, the drawing stays on the main thread - it touches a view hierarchy, and the
     * same thread must own those views every time - while the compression, the most expensive step at
     * about 75 ms, moves off it.
     *
     * Overriding the whole request also skips the base class's `DataStore` read, which this path
     * never used: the pipeline reads the data itself, and only when it has changed.
     */
    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        // No tap action while the watch is dozing. A slot answers taps wherever it is drawn, so with
        // one attached the first tap on a sleeping watch was consumed by this complication and opened
        // AAPS instead of waking the screen - reported on a Galaxy Watch 4, where the tap highlight
        // was visible on the full-screen image. A first tap should wake; the menu is one tap away
        // afterwards, when this is rebuilt with its action restored.
        val tapIntent =
            if (isAmbient(this)) null
            else ComplicationTapActivity.getTapActionIntent(
                context = this,
                provider = ComponentName(this, getProviderCanonicalName()),
                complicationId = request.complicationInstanceId,
                action = getComplicationAction()
            )
        scope.launch {
            val data = try {
                buildFromIcon(request.complicationType, iconFor(frameBytes()), tapIntent)
            } catch (t: Throwable) {
                // Throwable, not Exception, on purpose. This draws through androidx.wear.watchface,
                // and the manifest declares `wear-sdk` as an optional shared library. On a watch
                // where it is missing, touching a class that needs it raises NoClassDefFoundError -
                // an Error, which a `catch (e: Exception)` lets straight through. The coroutine
                // would then die without ever answering the listener, the slot would stay empty for
                // good, and nothing would be logged: a black watch face with no clue why. Reported
                // from a Galaxy Watch 7, the hardware none of the developers can test on.
                aapsLogger.error(LTag.WEAR, "${javaClass.simpleName}: frame failed (${t.javaClass.simpleName}: ${t.message})", t)
                null
            }
            listener.onComplicationData(data)
        }
    }

    /** A prepared frame if one fits this second, otherwise a freshly built one. */
    private suspend fun frameBytes(): ByteArray {
        // Read here, as late as it can be: the watch can doze between one request and the next.
        val ambient = isAmbient(this)
        // The face without seconds serves three callers, and none of them wants a second hand.
        //
        // The always-on slot asks for it by definition. **In always-on nobody wants seconds at all**:
        // the awake slot is drawn at alpha 0 there, whatever the wearer chose, so composing a fresh
        // picture for it would be work nobody could see - always-on used to cost a full frame every
        // five to thirteen seconds for exactly that. And **when the seconds are switched off**, by
        // the zip or by the wearer, the two pictures are the same picture: drawing both would pay two
        // compressions a minute to produce two identical images.
        if (withoutSeconds || ambient || !showsSeconds()) {
            minuteFrame.take(System.currentTimeMillis())?.let { minute ->
                noteDrawn(minute.second)
                aapsLogger.debug(LTag.WEAR, "${javaClass.simpleName}: served the minute frame, no seconds (${minute.bytes.size / 1024} kB)")
                return minute.bytes
            }
            return buildMinuteFrame()
        }
        // The second this delivery will be showing, not the one it starts in - see [landingSecond].
        // The same answer serves both paths: a prepared frame already carries the second it depicts,
        // so asking the queue for the landing second is asking for the frame that will be correct.
        val second = landingSecond()
        queue(aapsLogger).take(second, token(), ambient)?.let { prepared ->
            // What the wearer sees is this frame's own second, which need not be the one asked for -
            // the queue serves the nearest within tolerance. Recording it is what keeps the promise
            // that time only moves forward: without this the clamp guarded the second we *asked* for
            // while the wearer saw a different one, and a frame served slightly ahead could be
            // followed by one built slightly behind. On a Galaxy Watch 4 that showed as the second
            // and minute hands stepping back.
            noteDrawn(prepared.second)
            // Silent while the seconds follow one another - see [servedFrameWarning]
            servedFrameWarning(lastServedSecond, prepared.second)
                ?.let { aapsLogger.debug(LTag.WEAR, "${javaClass.simpleName}: $it") }
            lastServedSecond = prepared.second
            return prepared.bytes
        }
        val metrics = resources.displayMetrics
        val pipeline = pipeline(this, aapsLogger)
        // The mode is read once more inside, just before the seconds would be drawn - the wrist can
        // drop while this frame is being made
        val bitmap = pipeline.compose(second, ambient, metrics.widthPixels, metrics.heightPixels) { isAmbient(this) }
        val bytes = withContext(Dispatchers.Default) { pipeline.encode(bitmap) }
        aapsLogger.debug(
            LTag.WEAR,
            "${javaClass.simpleName}: built frame ${bytes.size / 1024} kB in ${pipeline.lastFrameMs} ms" +
                " (ambient=$ambient enableSecond=${warmWatchFace?.enableSecond} prepared=${queue(aapsLogger).size})"
        )
        return bytes
    }

    /**
     * A **still** picture of the watch face, never a live render.
     *
     * Two reasons the render path cannot be used, either sufficient. The base class builds preview
     * data on the binder thread, and inflating a view hierarchy off the main thread is not allowed.
     * And a preview is requested whenever anyone browses a complication picker - on a watch still
     * running the code-based Custom watch face, rendering there would build a second
     * `CustomWatchface` beside the live one, and the two share process-wide state.
     *
     * The androidx contract asks for a fixed preview anyway: `getPreviewData` should show
     * representative content, not live data.
     *
     * It uses **the loaded zip's own picture** rather than the built-in default, because this is what
     * the watch face editor renders when the user long-presses the face - showing an unrelated design
     * there is confusing. Falls back to the built-in image when no zip has been drawn yet.
     */
    /**
     * The picture the system shows for this watch face before it is running.
     *
     * **Deliberately the app's own artwork, and never the loaded zip.** The system asks for this
     * once, very early, and keeps the answer for good. Measured on a Wear 6 emulator: it was not
     * asked again after the app restarted, after the app's data was wiped, after the face was pushed
     * again, or after the watch rebooted - and the picker went on showing a design the watch no
     * longer had. A wearer who changes design would be left looking at the old one for ever, with no
     * way to correct it, which is worse than a picture that is honestly generic.
     *
     * Their own design is still shown wherever we control the drawing and can keep it current: the
     * watch face itself, and the Custom row in the AAPS settings menu.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val contentDescription = PlainComplicationText.Builder(text = getString(label)).build()
        val icon = Icon.createWithResource(this, R.drawable.watchface_custom)

        return when (type) {
            ComplicationType.PHOTO_IMAGE -> PhotoImageComplicationData.Builder(
                photoImage = icon,
                contentDescription = contentDescription
            ).build()

            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                smallImage = SmallImage.Builder(image = icon, type = SmallImageType.PHOTO).build(),
                contentDescription = contentDescription
            ).build()

            else                         -> null
        }
    }

    /** A tap lands anywhere on a full-screen picture, so it opens the menu rather than one detail screen. */
    override fun getComplicationAction(): ComplicationAction = ComplicationAction.MENU
}
