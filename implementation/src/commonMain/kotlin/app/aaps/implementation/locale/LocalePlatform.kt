package app.aaps.implementation.locale

/**
 * The device's language and country - the only part of [LocaleDependentSettingImpl] a platform must
 * answer.
 *
 * An `expect object` rather than an injected interface, unlike `DateFormatPlatform`: that one needs a
 * `Context` for its Android answer and an `expect object` has no constructor to give one to. Reading
 * the locale needs nothing, so the lighter form is enough. The rule that uses these values is
 * [ntpServerFor], which is a plain function and is where the test goes.
 */
internal expect object LocalePlatform {

    /** ISO 639 language code of the device, for example `zh`. Empty when the platform has none. */
    val language: String

    /** ISO 3166 country code of the device, for example `CN`. Empty when the platform has none. */
    val country: String
}
