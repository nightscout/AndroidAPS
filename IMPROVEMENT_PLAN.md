# Compose UI Improvement Plan

Findings from code review of the new Compose UI. Organized by priority and area.

---

## 1. Architecture & Maintainability

### 1.1 MainScreen Decomposition

- [ ] Extract maintenance dialog chain (export state machine, confirmation dialogs) into
  `MaintenanceDialogs` composable
- [ ] Extract bottom sheet management (treatments, manage, automation) into dedicated composables or
  a coordinator
- [ ] Extract version overlay into `VersionOverlay` composable
- [ ] Reduce MainScreen parameter count by grouping related callbacks into sealed class actions (
  e.g., `MainAction`)

### 1.2 Callback Threading

- [ ] Evaluate replacing N callback parameters with `onAction: (SealedAction) -> Unit` pattern for
  screens with >10 callbacks
- [ ] Consider `CompositionLocal` for deeply-shared singletons (`DateUtil`, `Preferences`, `Config`)
  instead of threading them through 3+ composable layers

### 1.3 TreatmentsScreen Toolbar Workaround

- [ ] Replace `allowedToolbarPage` + `key(allowedToolbarPage)` forced recomposition pattern with a
  callback-based or shared `StateFlow<ToolbarConfig>` approach
- [ ] Fix `TreatmentTab` data class storing `@Composable () -> Unit` lambda (breaks `equals`/
  `hashCode`) — consider using an index-based approach or `@Immutable` annotation with manual
  equality

---

## 2. Material 3 Compliance

### 2.1 Navigation Bar

- [ ] Replace `NavigationBar` + `NavigationBarItem(selected = false)` in `MainNavigationBar` with
  `BottomAppBar` + `IconButton` — current items are actions, not navigation destinations

### 2.2 Theme Completeness

- [x] Add custom `Typography` to `AapsTheme` with semantic text styles (e.g., `bgValue`,
  `chipLabel`, `sectionHeader`) instead of inline `fontSize`/`fontWeight` overrides
  — `AapsTypography.kt` with 6 styles, `compositionLocalOf`, `aapsTypography()` factory
- [x] Create a centralized spacing/dimension system (e.g., `AapsTheme.spacing.small/medium/large` or
  dimension constants) to replace scattered hardcoded `dp` values
  — `AapsSpacing.kt` with generic scale + domain-specific chip/BG dimensions
- [x] Audit and replace inline `sp`/`dp` overrides in `BgInfoSection` (`50.sp`, `17.sp`, `126.dp`)
  with theme-defined values
  — Also migrated all 7 chip composables to use `AapsSpacing.*` and `AapsTheme.typography.*`

---

## 3. Consistency

### 3.1 State Collection

- [x] Replace all `collectAsState()` with `collectAsStateWithLifecycle()` for consistency and
  lifecycle awareness — 42 instances across 14 files

### 3.2 Icons

- [x] Migrate `StatusItem` from XML drawable `iconRes: Int` to Compose `icon: ImageVector` —
  replaced `ic_cp_age_sensor/insulin/cannula/battery` with
  `IcCgmInsert/IcPumpCartridge/IcCannulaChange/IcPumpBattery`
- [x] Create `IcPatchPump` Compose icon (replacing `ic_patch_pump_outline` XML drawable)
- [x] Fix `IcCanulaChange` typo → renamed to `IcCannulaChange` across all files

### 3.3 RxJava in Compose

- [ ] Evaluate migrating `AapsTheme` RxBus preference listener to `SharedFlow` or `snapshotFlow` for
  idiomatic Compose reactivity (low priority — works correctly as-is)

---

## 4. Accessibility

### 4.1 Screen Reader Support

- [x] Add semantic content description to `BgInfoSection` — Box now has semantics block with BG
  value, trend, delta, time, and outdated status
- [x] Add `contentDescription` to `IcSettingsOff` simple mode icon (`R.string.simple_mode`)
- [x] Add `contentDescription` to expand/collapse icons in `OverviewStatusSection` (
  `R.string.expand` / `R.string.collapse`)

### 4.2 Color & Contrast

- [x] ~~Fix `CompactStatusItem` using `Color.Unspecified` tint~~ — Resolved by migrating to Compose
  `ImageVector` icons with `MaterialTheme.colorScheme.onSurfaceVariant` tint
- [ ] Audit color contrast ratios for critical medical information (BG values, status indicators)
  against WCAG AA standards

---

## 5. Usability

### 5.1 Haptic Feedback

- [x] Add haptic feedback to `RepeatingIconButton` in `SliderWithButtons` for tactile confirmation
  when stepping through insulin/carb values
  — `TextHandleMove` on each tick and initial tap
- [x] Consider haptic feedback on chip taps for state-changing actions (temp target, running mode)
  — `LongPress` haptic on TempTargetChip, RunningModeChip, ProfileChip

### 5.2 Loading States

- [x] Add loading skeletons or shimmer placeholders for screens that load data asynchronously (
  treatments list, statistics)
  — Treatment screens already use `ContentContainer` with `isLoading`; Stats uses `LoadingSection`
  with `Crossfade`. Upgraded `ContentContainer` with `Crossfade` transitions for smooth state
  changes.
- [x] Ensure empty states have clear messaging (not just blank space)
  — `ContentContainer` now shows `SearchOff` icon + text instead of plain text

### 5.3 Version Overlay

- [x] Move version overlay from `TopStart` to `TopEnd` (right side) to avoid overlap with BG info
  area and progress bar

---

## 6. Robustness

### 6.1 Date/Time Handling

- [ ] Add defensive null/exception handling in `CareDialogScreen` date picker conversion chain (
  `millis → LocalDateTime → merged → toInstant`)
- [ ] Handle timezone change edge case between picker open and selection

### 6.2 Error States

- [ ] Audit dialogs and input screens for missing error state UI (e.g., network failure during
  export, invalid input values)

---

## Notes

- Items are roughly ordered by impact within each section
- Architecture items (Section 1) provide the most long-term benefit but are higher effort
- Accessibility items (Section 4) are important for a medical app with diverse users
- Consistency items (Section 3) are low-effort quick wins
