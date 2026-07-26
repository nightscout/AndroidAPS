package app.aaps.core.keys.interfaces

interface IntentPreferenceKey : PreferenceKey {

    /**
     * String resource ID for URL (for PreferenceType.URL).
     * If set, the URL will be resolved at runtime using stringResource().
     */
    val urlResId: Int?
        get() = null

    /**
     * Activity class to launch (for PreferenceType.ACTIVITY).
     * If set, clicking the preference will launch this activity.
     */
    val activityClass: Class<*>?
        get() = null

    /**
     * String resource ID for confirmation dialog message.
     * When set, clicking this preference shows an OK/Cancel dialog before executing onClick.
     * The dialog title uses [titleResId].
     */
    val confirmationMessageResId: Int?
        get() = null

    /**
     * Runtime-attached click handler.
     * When set, this takes precedence over other handlers.
     */
    val onClick: (() -> Unit)?
        get() = null

    /**
     * Runtime-attached activity class.
     * When set, this takes precedence over [activityClass].
     */
    val runtimeActivityClass: Class<*>?
        get() = null

    /**
     * Runtime-attached URL.
     * When set, this takes precedence over [urlResId].
     */
    val runtimeUrl: String?
        get() = null

    /**
     * Runtime-attached Compose screen content.
     * When set, clicking the preference navigates to this Compose screen
     * instead of launching an Activity.
     *
     * At runtime this holds `@Composable (onBack: () -> Unit) -> Unit`.
     * Typed as Any? because core:keys has no Compose dependency.
     */
    val composeScreen: Any?
        get() = null
}

/**
 * Wrapper that attaches a click handler to an IntentPreferenceKey.
 */
class IntentKeyWithClick(
    private val delegate: IntentPreferenceKey,
    override val onClick: () -> Unit
) : IntentPreferenceKey by delegate

/**
 * Wrapper that attaches an activity class to an IntentPreferenceKey.
 */
class IntentKeyWithActivity(
    private val delegate: IntentPreferenceKey,
    override val runtimeActivityClass: Class<*>
) : IntentPreferenceKey by delegate

/**
 * Wrapper that attaches a URL to an IntentPreferenceKey.
 */
class IntentKeyWithUrl(
    private val delegate: IntentPreferenceKey,
    override val runtimeUrl: String
) : IntentPreferenceKey by delegate

/**
 * Creates a new IntentPreferenceKey with a click handler attached.
 */
fun IntentPreferenceKey.withClick(onClick: () -> Unit): IntentPreferenceKey =
    IntentKeyWithClick(this, onClick)

/**
 * Creates a new IntentPreferenceKey with an activity class attached.
 */
fun IntentPreferenceKey.withActivity(activityClass: Class<*>): IntentPreferenceKey =
    IntentKeyWithActivity(this, activityClass)

/**
 * Creates a new IntentPreferenceKey with a URL attached.
 */
fun IntentPreferenceKey.withUrl(url: String): IntentPreferenceKey =
    IntentKeyWithUrl(this, url)

/**
 * Wrapper that attaches Compose screen content to an IntentPreferenceKey.
 */
class IntentKeyWithCompose(
    private val delegate: IntentPreferenceKey,
    override val composeScreen: Any
) : IntentPreferenceKey by delegate

/**
 * Creates a new IntentPreferenceKey with Compose screen content attached.
 * The [content] lambda should be `@Composable (onBack: () -> Unit) -> Unit`.
 */
fun IntentPreferenceKey.withCompose(content: Any): IntentPreferenceKey =
    IntentKeyWithCompose(this, content)