package app.aaps.core.objects.wizard

import app.aaps.annotations.OpenForTesting
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.core.utils.MidnightUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class QuickWizardMode(val value: Int) {
    WIZARD(0),
    INSULIN(1),
    CARBS(2);

    companion object {

        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: WIZARD
    }
}

class QuickWizardEntry(
    aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    // Plain factories rather than javax.inject.Provider - see QuickWizard for why.
    private val bolusWizardProvider: () -> BolusWizard,
    private val quickWizardProvider: () -> QuickWizard
) {

    // for mock
    @OpenForTesting
    class Time {

        fun secondsFromMidnight(): Int = MidnightUtils.secondsFromMidnight()

    }

    var time = Time()

    /**
     * The preset itself, as plain data.
     *
     * Not aliased to the element inside [QuickWizard]'s array: anything that changes [data] has to
     * hand the entry back to [QuickWizard] to be written, so a field write is never a silent store.
     */
    var data: QuickWizardEntryData = QuickWizardEntryData(guid = randomGuid(), validTo = 86340)
    var position: Int = -1

    companion object {

        // Defined once in QuickWizardEntryData, which is common code. Re-exposed here so
        // `QuickWizardEntry.ALWAYS` style call sites keep working.
        const val ALWAYS = QuickWizardEntryData.ALWAYS
        const val NEVER = QuickWizardEntryData.NEVER
        const val POSITIVE_ONLY = QuickWizardEntryData.POSITIVE_ONLY
        const val NEGATIVE_ONLY = QuickWizardEntryData.NEGATIVE_ONLY
        const val DEVICE_ALL = QuickWizardEntryData.DEVICE_ALL
        const val DEVICE_PHONE = QuickWizardEntryData.DEVICE_PHONE
        const val DEVICE_WATCH = QuickWizardEntryData.DEVICE_WATCH
        const val COOLDOWN_MILLIS = QuickWizardEntryData.COOLDOWN_MILLIS

        @OptIn(ExperimentalUuidApi::class)
        fun randomGuid(): String = Uuid.random().toString()
    }

    fun from(entry: QuickWizardEntryData, position: Int): QuickWizardEntry {
        data = entry
        this.position = position
        return this
    }

    fun isActive(): Boolean {
        val now = time.secondsFromMidnight()
        val inTimeRange = if (validTo() >= validFrom()) now in validFrom()..validTo()
        else now >= validFrom() || now <= validTo() // wraps midnight
        if (!inTimeRange || !forDevice(DEVICE_PHONE)) return false
        val timeRangeSeconds = if (validTo() >= validFrom()) validTo() - validFrom()
        else (86400 - validFrom()) + validTo()
        val needsCooldown = timeRangeSeconds < 4 * 3600
        return !needsCooldown || (dateUtil.now() - lastUsed() > COOLDOWN_MILLIS)
    }

    suspend fun doCalc(profile: Profile, profileName: String, lastBG: InMemoryGlucoseValue): BolusWizard {
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        //BG
        var bg = 0.0
        if (useBG() == ALWAYS) {
            bg = lastBG.valueToUnits(profileFunction.getUnits())
        }
        // COB
        val cob =
            if (useCOB() == ALWAYS) iobCobCalculator.getCobInfo("QuickWizard COB").displayCob ?: 0.0
            else 0.0
        // IOB
        var uIOB = false
        if (useIOB() == ALWAYS) {
            uIOB = true
        }

        var uPositiveIOBOnly = false
        if (usePositiveIOBOnly() == ALWAYS) {
            uPositiveIOBOnly = true
        }
        // SuperBolus
        var superBolus = false
        if (useSuperBolus() == ALWAYS && preferences.get(BooleanKey.OverviewUseSuperBolus)) {
            superBolus = true
        }
        if (loop.runningMode() == RM.Mode.SUPER_BOLUS) superBolus = false
        // Trend
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        var trend = false
        if (useTrend() == ALWAYS) {
            trend = true
        } else if (useTrend() == POSITIVE_ONLY && glucoseStatus != null && glucoseStatus.shortAvgDelta > 0) {
            trend = true
        } else if (useTrend() == NEGATIVE_ONLY && glucoseStatus != null && glucoseStatus.shortAvgDelta < 0) {
            trend = true
        }
        val percentage = percentage()
        return bolusWizardProvider().doCalc(
            profile,
            profileName,
            tempTarget,
            carbs(),
            cob,
            bg,
            0.0,
            percentage,
            true,
            useCOB() == ALWAYS,
            uIOB, //always use or don't both bolus
            uIOB, // & basal IOB
            superBolus,
            useTempTarget() == ALWAYS,
            trend,
            useAlarm() == ALWAYS,
            buttonText(),
            carbTime(),
            positiveIOBOnly = uPositiveIOBOnly,
            source = Sources.QuickWizard
        ) //tbc, ok if only quickwizard, but if other sources elsewhere use Sources.QuickWizard
    }

    fun mode(): QuickWizardMode = QuickWizardMode.fromValue(data.mode)

    fun insulin(): Double = data.insulin

    fun guid(): String = data.guid

    fun device(): Int = data.device

    fun forDevice(device: Int) = device() == device || device() == DEVICE_ALL

    fun buttonText(): String = data.buttonText

    fun carbs(): Int = data.carbs

    fun validFrom(): Int = data.validFrom

    fun validTo(): Int = data.validTo

    fun useBG(): Int = data.useBG

    fun useCOB(): Int = data.useCOB

    fun useIOB(): Int = data.useIOB

    fun usePositiveIOBOnly(): Int = data.usePositiveIOBOnly

    fun useTrend(): Int = data.useTrend

    fun useSuperBolus(): Int = data.useSuperBolus

    fun useTempTarget(): Int = data.useTempTarget

    fun percentage(): Int = data.percentage

    fun useEcarbs(): Int = data.useEcarbs

    fun carbs2(): Int = data.carbs2

    fun time(): Int = data.time

    fun duration(): Int = data.duration

    fun carbTime(): Int = data.carbTime

    fun useAlarm(): Int = data.useAlarm

    fun lastUsed(): Long = data.lastUsed

    fun markAsUsed() {
        data = data.copy(lastUsed = dateUtil.now())
        // The write-back is explicit now. It used to work only because `storage` WAS the element
        // inside QuickWizard's array, so a plain save() picked the new value up on its own.
        quickWizardProvider().addOrUpdate(this)
    }
}
