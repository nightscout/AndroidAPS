package app.aaps.plugins.sync.nfcCommands


/**
 * Central registry for all JSON keys used in NFC command serialization.
 * Using constants ensures consistency between UI state management and action execution.
 */
object NfcJsonKeys {
    const val CODE = "code"
    const val PARAMS = "params"
    const val TAG_NAME = "tagname"
    
    const val AMOUNT = "amount"     // Used for Insulin (Units) and Carbs (Grams)
    const val GLUCOSE = "glucose"   // Used for Temp Targets
    const val PERCENT = "percent"   // Used for Profile Switch, Wizard, and Basal PCT
    const val DURATION = "duration" // Used for TT, Basal, and Suspend (Minutes)
    const val RATE = "rate"         // Used for Absolute Basal (U/h)
    const val PROFILE_NAME = "profileName"
    const val SCENE_ID = "sceneId"
    const val IS_MEAL = "isMeal"    // Bolus checkbox

    // Bolus Wizard specific options
    const val USE_BG = "useBg"
    const val USE_TT = "useTT"
    const val USE_TREND = "useTrend"
    const val USE_IOB = "useIOB"
    const val USE_COB = "useCOB"
}
