package app.aaps.core.ui.compose.siteRotation

import app.aaps.core.data.model.TE.Location
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/**
 * The translated name of a site location. The one mapping for every screen: the Translator uses it
 * for a plain string, Compose code resolves it with `stringResource`. [Location.text] is the English
 * database value and must never be shown to the user.
 */
fun Location.textRef(): TextRef =
    when (this) {
        Location.FRONT_RIGHT_UPPER_CHEST   -> CoreUiStrings.location_front_right_upper_chest
        Location.FRONT_LEFT_UPPER_CHEST    -> CoreUiStrings.location_front_left_upper_chest
        Location.SIDE_RIGHT_UPPER_ARM      -> CoreUiStrings.location_side_right_upper_arm
        Location.SIDE_LEFT_UPPER_ARM       -> CoreUiStrings.location_side_left_upper_arm
        Location.BACK_RIGHT_UPPER_ARM      -> CoreUiStrings.location_back_right_upper_arm
        Location.BACK_LEFT_UPPER_ARM       -> CoreUiStrings.location_back_left_upper_arm
        Location.SIDE_RIGHT_UPPER_ABDOMEN  -> CoreUiStrings.location_side_right_upper_abdomen
        Location.SIDE_LEFT_UPPER_ABDOMEN   -> CoreUiStrings.location_side_left_upper_abdomen
        Location.SIDE_RIGHT_LOWER_ABDOMEN  -> CoreUiStrings.location_side_right_lower_abdomen
        Location.SIDE_LEFT_LOWER_ABDOMEN   -> CoreUiStrings.location_side_left_lower_abdomen
        Location.FRONT_RIGHT_UPPER_ABDOMEN -> CoreUiStrings.location_front_right_upper_abdomen
        Location.FRONT_LEFT_UPPER_ABDOMEN  -> CoreUiStrings.location_front_left_upper_abdomen
        Location.FRONT_RIGHT_LOWER_ABDOMEN -> CoreUiStrings.location_front_right_lower_abdomen
        Location.FRONT_LEFT_LOWER_ABDOMEN  -> CoreUiStrings.location_front_left_lower_abdomen
        Location.BACK_RIGHT_BUTTOCK        -> CoreUiStrings.location_back_right_buttock
        Location.BACK_LEFT_BUTTOCK         -> CoreUiStrings.location_back_left_buttock
        Location.BACK_RIGHT_LOWER_BACK     -> CoreUiStrings.location_back_right_lower_back
        Location.BACK_LEFT_LOWER_BACK      -> CoreUiStrings.location_back_left_lower_back
        Location.FRONT_RIGHT_UPPER_THIGH   -> CoreUiStrings.location_front_right_upper_thigh
        Location.FRONT_LEFT_UPPER_THIGH    -> CoreUiStrings.location_front_left_upper_thigh
        Location.FRONT_RIGHT_LOWER_THIGH   -> CoreUiStrings.location_front_right_lower_thigh
        Location.FRONT_LEFT_LOWER_THIGH    -> CoreUiStrings.location_front_left_lower_thigh
        Location.SIDE_RIGHT_UPPER_THIGH    -> CoreUiStrings.location_side_right_upper_thigh
        Location.SIDE_LEFT_UPPER_THIGH     -> CoreUiStrings.location_side_left_upper_thigh
        Location.SIDE_RIGHT_LOWER_THIGH    -> CoreUiStrings.location_side_right_lower_thigh
        Location.SIDE_LEFT_LOWER_THIGH     -> CoreUiStrings.location_side_left_lower_thigh
        Location.NONE                      -> CoreUiStrings.unknown
    }
