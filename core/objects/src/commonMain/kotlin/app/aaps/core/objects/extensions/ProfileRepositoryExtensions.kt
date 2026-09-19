package app.aaps.core.objects.extensions

import app.aaps.core.interfaces.profile.ProfileRepository

/**
 * Snapshot the current profile-name list. Equivalent to
 * `profiles.value.map { it.name }` but reads cleaner at call sites where the caller
 * only needs the names (dropdowns, validators, lookups).
 *
 * `profiles` and `profile` (the by-name JSON projection) are always written together
 * under the repository's mutex, so reading either is consistent. We read the in-memory
 * list directly because it's the source of truth — the JSON is a derived projection.
 */
fun ProfileRepository.profileNames(): List<String> =
    profiles.value.map { it.name }
