package app.aaps.core.data.model

data class NE(
    //val apsResults: List<APSResult>,
    val bolusCalculatorResults: List<BCR>,
    val boluses: List<BS>,
    val carbs: List<CA>,
    val effectiveProfileSwitches: List<EPS>,
    val extendedBoluses: List<EB>,
    val glucoseValues: List<GV>,
    val runningModes: List<RM>,
    //val preferencesChanges: List<PreferenceChange>,
    val profileSwitches: List<PS>,
    val temporaryBasals: List<TB>,
    val temporaryTarget: List<TT>,
    val therapyEvents: List<TE>,
    val totalDailyDoses: List<TDD>,
    //val versionChanges: List<VersionChange>,
    val heartRates: List<HR>,
)
