package app.aaps.implementation.resources

/**
 * Whether text should be shortened for a narrow screen - "3d4h" rather than "3 days 4 hours".
 *
 * Android answers this in `ResourceHelperImpl` from the `isTablet` boolean resource, so a phone gets
 * the short form and a tablet the long one. [GeneratedTextResolver] had no answer at all and
 * returned false on every target, so an iPhone showed the long form in the status light row where an
 * Android phone of the same width showed the short one - the row most at risk of wrapping.
 *
 * The comment that used to stand in for this said "a desktop window and an iPad are both wide". True
 * of those two, and it forgot the phone.
 */
expect fun isCompactScreen(): Boolean
