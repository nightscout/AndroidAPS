package app.aaps.core.interfaces.profile

/**
 * Types of profile validation errors, corresponding to profile editor tabs/sections.
 */
enum class ProfileErrorType {

    /** General errors not tied to a specific section (e.g., no profile selected) */
    GENERAL,

    /** Profile name errors (empty, contains dot) */
    NAME,

    /** Duration of Insulin Action errors */
    DIA,

    /** Insulin-to-Carb ratio errors */
    IC,

    /** Insulin Sensitivity Factor errors */
    ISF,

    /** Basal rate errors */
    BASAL,

    /** Target glucose range errors */
    TARGET
}

/**
 * Structured profile validation error.
 *
 * @param type The category/section of the error
 * @param message Human-readable error message (localized)
 */
data class ProfileValidationError(
    val type: ProfileErrorType,
    val message: String
)
