package app.aaps.plugins.sync.nfcCommands

/**
 * Whether [params] actually carries a value for this argument.
 *
 * Every value in [NfcParams] that a command has to supply is nullable, so null means "not set" rather
 * than "zero". This is the only place that says which field belongs to which [ArgType]; an action reads
 * its own field, and the build screen writes it, but nothing else has to know the whole mapping.
 *
 * Booleans are never missing: `false` and `true` are both real answers, and [NfcParams] gives them a
 * declared default, so there is nothing to miss.
 */
internal fun ArgType.isSetIn(params: NfcParams): Boolean = when (this) {
    ArgType.INSULIN                                      -> params.insulin != null
    ArgType.AMOUNT_GRAMS                                 -> params.carbs != null
    ArgType.RATE                                         -> params.rate != null
    ArgType.PERCENT                                      -> params.percent != null
    ArgType.DURATION                                     -> params.duration != null
    ArgType.GLUCOSE_TARGET                               -> params.glucose != null
    // A blank name is as useless as no name, so it counts as missing.
    ArgType.PROFILE_NAME                                 -> !params.profileName.isNullOrBlank()
    ArgType.SCENE_ID                                     -> !params.sceneId.isNullOrBlank()
    ArgType.MEAL_CHECK, ArgType.BOLUS_WIZARD_OPTIONS, ArgType.NONE -> true
}
