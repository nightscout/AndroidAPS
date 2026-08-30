package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * AAPS alarm audio on iOS, through `AVAudioPlayer`.
 *
 * ## Why this can alarm at all
 *
 * An `AVAudioSession` on category `playback` ignores the hardware mute switch, so this rings on a
 * silenced phone with no special entitlement. That is worth stating clearly because it is easy to
 * conclude the opposite: the Critical Alerts entitlement is needed for a *notification* to make
 * noise when the app is not running, which is a different problem from the app playing a sound
 * itself. While AAPS is alive - which for a looper means whenever it is holding its Bluetooth
 * connection - this is enough.
 *
 * What it does **not** cover: the app not running at all. There is no counterpart to Android's
 * foreground service, so an alarm cannot start from nothing. See `_docs/ios_blockers.md`.
 *
 * ## Threading
 *
 * Everything runs on the main dispatcher, which is how the Android class confines its state to the
 * main looper. `play` may be called from any thread - the notification registry calls it while
 * holding its own lock - so nothing here touches [player] outside that dispatcher.
 */
@OptIn(ExperimentalForeignApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IosAlarmSoundPlayer @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : AlarmSoundPlayer {

    private val scope = CoroutineScope(Dispatchers.Main)

    // --- main-dispatcher-confined state ---
    private var player: AVAudioPlayer? = null
    private var currentOwner: String? = null
    private var rampJob: Job? = null

    /**
     * [postedAtElapsedRealtime] is ignored: it exists so Android can wait out a notification's own
     * one-shot channel sound before starting the loop, and an iOS notification posted by
     * `IosSystemNotificationPlatform` deliberately carries no sound.
     */
    override fun play(sound: AlarmSound, ownerTag: String, postedAtElapsedRealtime: Long) {
        scope.launch { doPlay(sound, ownerTag) }
    }

    override fun stop(ownerTag: String) {
        scope.launch {
            // Owner scoped: the full screen path tearing down must not silence an alarm the
            // notification path started, and the other way round.
            if (currentOwner != ownerTag) return@launch
            doStop()
        }
    }

    private fun doPlay(sound: AlarmSound, ownerTag: String) {
        doStop()
        val url = urlFor(sound) ?: run {
            aapsLogger.error(LTag.NOTIFICATION, "Alarm sound ${fileName(sound)} is not in the app bundle, cannot alarm")
            return
        }
        activateSession()

        val created = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val p = AVAudioPlayer(contentsOfURL = url, error = error.ptr)
            error.value?.let { aapsLogger.error(LTag.NOTIFICATION, "Cannot open ${fileName(sound)}: ${it.localizedDescription}") }
            p
        }
        // -1 loops until stopped, which is what an alarm has to do.
        created.numberOfLoops = -1

        val ramping = preferences.get(BooleanKey.AlertIncreaseVolume)
        val prepared = created.prepareToPlay()
        if (!created.play()) {
            aapsLogger.error(
                LTag.NOTIFICATION,
                "AVAudioPlayer refused to start for ${fileName(sound)}: prepared=$prepared duration=${created.duration} " +
                    "volume=${created.volume} loops=${created.numberOfLoops} url=${created.url?.lastPathComponent} " +
                    "category=${AVAudioSession.sharedInstance().category}"
            )
            return
        }

        // Volume is set after the start, not before it: AVAudioPlayer will not begin at volume 0,
        // where Android's MediaPlayer happily does. Starting silent and turning it up immediately is
        // the same thing from the listener's side, and it is what lets the ramp begin from nothing.
        created.volume = if (ramping) 0f else 1f

        player = created
        currentOwner = ownerTag
        aapsLogger.debug(LTag.NOTIFICATION, "Alarm ${sound.name} playing for $ownerTag, ramping=$ramping")
        if (ramping) rampJob = scope.launch { runRamp(created) }
    }

    private fun doStop() {
        rampJob?.cancel()
        rampJob = null
        player?.stop()
        player = null
        currentOwner = null
        // The session is left active on purpose: deactivating it on every alarm would interrupt
        // whatever else the phone is playing twice per alarm instead of once.
    }

    private suspend fun runRamp(target: AVAudioPlayer) {
        val ramp = AlarmVolumeRamp()
        ramp.start()
        delay(AlarmVolumeRamp.INITIAL_SILENT_TIME_MS)
        while (scope.isActive && ramp.hasMore() && player === target) {
            target.volume = ramp.next()
            delay(ramp.delayMillis())
        }
    }

    /**
     * Errors are captured rather than discarded.
     *
     * Passing null for the error pointer here cost real time once: the player refused to start and
     * there was nothing to say why, because every failure had been thrown away at the call site.
     */
    private fun activateSession() = memScoped {
        val session = AVAudioSession.sharedInstance()
        val error = alloc<ObjCObjectVar<NSError?>>()
        // playback is the category that ignores the mute switch. Without it an alarm is silent on a
        // phone with the ringer off, which is exactly when it is needed.
        if (!session.setCategory(AVAudioSessionCategoryPlayback, error = error.ptr)) {
            aapsLogger.error(LTag.NOTIFICATION, "Cannot set the playback category: ${error.value?.localizedDescription}")
        }
        if (!session.setActive(true, error = error.ptr)) {
            aapsLogger.error(LTag.NOTIFICATION, "Cannot activate the audio session: ${error.value?.localizedDescription}")
        }
    }

    private fun urlFor(sound: AlarmSound): NSURL? =
        NSBundle.mainBundle.URLForResource(fileName(sound), withExtension = "mp3")

    /** Same four files as `:core:ui/res/raw` on Android, copied into the bundle by the build. */
    private fun fileName(sound: AlarmSound): String = when (sound) {
        AlarmSound.ALARM        -> "alarm"
        AlarmSound.URGENT_ALARM -> "urgentalarm"
        AlarmSound.ERROR        -> "error"
        AlarmSound.BOLUS_ERROR  -> "boluserror"
    }
}
