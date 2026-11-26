# Design Reviewer Memory

## Key Files
- Theme spacing: `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/AapsSpacing.kt`
- Shared icon component: `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/TonalIcon.kt` (40dp box, 24dp icon)
- Core composables: `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/` (AapsTopAppBar, AapsCard, etc.)
- Cards: `AapsCard` (an `ElevatedCard` on `surfaceContainerHigh`; its KDoc says why the older colour failed in dark mode)

## Sizing Conventions
- Standard icon in icon button (toolbar/list): 24dp
- TonalIcon container: 40dp box, 24dp icon inside
- Category row leading icon: 24dp (no container)
- Material minimum touch target: 48dp (use `minimumInteractiveComponentSize` or ensure 48dp hit area)
- IconButton default size: 48dp touch area, but visually can be constrained with `.size()`

## Color Conventions
- All icon tints use `MaterialTheme.colorScheme.onSurfaceVariant` for secondary/decorative icons
- Primary/active plugin icons use `MaterialTheme.colorScheme.primary`
- Do NOT use hardcoded colors; use `MaterialTheme.colorScheme.*` or the `AapsTheme` groups:
  `generalColors`, `elementColors`, `profileHelperColors`, `snackbarColors`

## Accessibility Patterns
- Decorative icons that are part of a labeled row: `contentDescription = null` is acceptable
- Action icons (IconButton) MUST have meaningful contentDescription
- Disabled alpha convention: `0.38f` (matches Material spec) — used inline as a local val, no shared constant in core/ui yet

## Patterns Seen
- QuickLaunchConfigScreen uses `OutlinedIconButton` for add/remove row actions
- DrawerMenuItem uses full-width clickable rows with icon + label + description
- ListItem trailing content uses default IconButton (48dp touch target, no size constraint)
