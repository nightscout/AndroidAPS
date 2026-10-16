package app.aaps.core.keys.interfaces

interface IntentPreferenceKey : PreferenceKey {

    /**
     * An intent key is an ACTION, not a value, so there is nothing for an export file to carry.
     *
     * Stated here rather than left to each enum. All six implementations already write
     * `override val exportable: Boolean = false` in their constructor and agree with this, but that is
     * a convention repeated six times, not a property of the type: a seventh enum that simply leaves
     * the line out inherits `true` from [NonPreferenceKey] and quietly becomes exportable. Putting it
     * on the interface makes the safe answer the one you get for free.
     *
     * The existing constructor declarations still win over this getter - a constructor parameter
     * shadows an interface default - but they set the same value, so they are now redundant rather
     * than load bearing.
     */
    override val exportable: Boolean get() = false

    /**
     * String resource ID for URL (for PreferenceType.URL).
     * If set, the URL will be resolved at runtime using stringResource().
     */
    val urlResId: Int?
        get() = null

    /**
     * Confirmation dialog message.
     * When set, clicking this preference shows an OK/Cancel dialog before executing onClick.
     * The dialog title uses [title].
     */
    /**
     * Platform neutral URL, as a [TextRef]. Preferred over [urlResId], which only multiplatform
     * code cannot supply - an Android resource id means nothing off Android.
     */
    val urlRef: TextRef?
        get() = null

    /**
     * Confirmation dialog message.
     * When set, clicking this preference shows an OK/Cancel dialog before executing onClick.
     * The dialog title uses [title].
     */
    val confirmationMessage: TextRef?
        get() = null

    /**
     * Runtime-attached click handler.
     * When set, this takes precedence over other handlers.
     */
    val onClick: (() -> Unit)?
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