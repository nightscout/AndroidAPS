# Hierarchical Protection System Refactor

**Status: Core implementation complete (2026-03-28). Needs runtime testing.**

## Goal

Replace the current independent protection checks with a hierarchical level-based system.
Single authentication prompt determines the highest granted level; screens adapt accordingly.

## Protection Levels

| Level | Name        | Auth methods         | Examples                                       |
|-------|-------------|----------------------|------------------------------------------------|
| 0     | APPLICATION | any configured       | App launch                                     |
| 1     | BOLUS       | any configured       | Profile activate, bolus, TBR, fill             |
| 2     | PREFERENCES | any configured       | Settings editing, profile editing              |
| 3     | MASTER      | master password only | Change master password, export/import settings |

- Granting level N implicitly grants all levels < N
- Master password always grants level 3 (and therefore everything)
- Biometric/custom PIN/custom password can grant at most level 2
- No master password set = no protection at all (all levels granted)

## Auth Query Flow

When the app requests authorization with `minimumLevel = X`:

1. No master password set → grant level 3 (no protection at all) → done
2. Active session at level >= X → grant that level → done
3. Collect all auth methods configured at levels >= X
4. If any of those levels uses biometric → show system `BiometricPrompt` with negative button "Use
   PIN/Password"
    - Biometric succeeds → grant highest level that uses biometric → done
    - User taps fallback → dismiss biometric → go to step 5
5. Show unified Compose dialog with PIN/password fields for methods configured at levels >= X
    - If no biometric was configured, this is the first prompt shown
6. User enters credential → match against all configured credentials at levels >= X:
    - Master password always matches → level 3
    - Settings custom password/PIN match → level 2
    - Bolus custom password/PIN match → level 1
    - App custom password/PIN match → level 0
    - No match → denied

Key: only methods at the requested minimum level or above are considered.
Master password is always accepted as it grants the highest level.

## Management Screen Mode from Granted Level

All management screens (Profile, Insulin, TempTarget, QuickWizard) use the same pattern:

```kotlin
requestAuthorization(minimumLevel = BOLUS) { grantedLevel ->
    if (grantedLevel != null) {
        val mode = if (grantedLevel >= PREFERENCES) ScreenMode.EDIT else ScreenMode.PLAY
        navigateToScreen(mode)
    }
}
```

- No protection set → level 3 granted silently → EDIT mode
- Bolus protection only → user authenticates → if credential matches higher level → EDIT, otherwise
  PLAY
- Both bolus + preferences set → user authenticates → mode depends on what they entered

## API Changes

### ProtectionCheck interface

```kotlin
// New method - replaces requestProtection for most use cases
fun requestAuthorization(
    minimumLevel: Protection,
    onResult: (grantedLevel: Protection?) -> Unit  // null = denied
)
```

### Session management

Store granted level + timestamp instead of per-protection timestamps.
If authenticated at level 2, levels 0 and 1 are also in active session.

## Manage Sheet Changes

Replace _EDIT with _PLAY variants (or collapse PLAY/EDIT into single ElementTypes):

- Manage sheet uses minimum level = BOLUS as entry point
- Screen mode determined by granted level, not by ElementType

## Files to Change

| File                           | Change                                                        |
|--------------------------------|---------------------------------------------------------------|
| `ProtectionCheck.kt`           | Add level ordering, new `requestAuthorization` method         |
| `ProtectionCheckImpl.kt`       | Implement hierarchical matching, unified session              |
| `ProtectionHost.kt`            | Handle new unified dialog + biometric flow                    |
| New: `AuthDialog.kt` (core/ui) | Compose dialog with PIN + password fields                     |
| `ManageBottomSheet.kt`         | Single ElementType per management screen                      |
| `ElementType.kt`               | Remove or simplify PLAY/EDIT pairs                            |
| `ComposeMainActivity.kt`       | Use `requestAuthorization`, pass mode from granted level      |
| `BiometricCheck.kt`            | Integrate with new flow (biometric fallback → unified dialog) |
| Management ViewModels          | Receive mode from navigation, auto-upgrade logic              |
| Export/import code             | Use level 3 (MASTER) protection                               |

## What Stays the Same

- `BiometricPrompt` usage (stable, current wrapper)
- User preference storage (which protection type per level)
- Protection timeout setting
- Master password as prerequisite for any protection

## TODO: Hierarchy Enforcement (UI + code together)

APPLICATION is independent (guards app access, orthogonal to in-app actions).
BOLUS → SETTINGS is a strict hierarchy: SETTINGS must be set to enable BOLUS.

Must be implemented as a single change — UI and code enforcement together,
otherwise user sees BOLUS=PIN in settings but it silently does nothing.

### UI changes (preferences screen):

- Reorder: SETTINGS protection, BOLUS protection (dependent), APPLICATION protection (independent)
- BOLUS dropdown: disabled (greyed out) when SETTINGS is NONE
- Auto-clear cascade: clearing SETTINGS also clears BOLUS stored value
- Labels: explain what each level protects and the dependency

### Code enforcement (safety net for imports/edge cases):

- `isProtectionConfigured(BOLUS)` returns false if SETTINGS is NONE, regardless of stored BOLUS
  value
- Same check in `collectAuthMethods` and `findHighestUnprotectedLevel`

### Files:

- `IntKey.kt` — reorder protection entries, add visibility condition for BOLUS
- `ProtectionCheckImpl.kt` — hierarchy enforcement in `isProtectionConfigured`
- Protection preferences UI — disabled state, auto-clear, labels
- `core/keys/PreferenceVisibility.kt` — may need `intNotEquals` helper