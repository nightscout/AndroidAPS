# Design Reviewer Memory

## Key Files
- Theme spacing: `core/ui/src/main/kotlin/app/aaps/core/ui/compose/AapsSpacing.kt`
- Shared icon component: `core/ui/src/main/kotlin/app/aaps/core/ui/compose/TonalIcon.kt` (40dp box, 24dp icon)
- Core composables: `core/ui/src/main/kotlin/app/aaps/core/ui/compose/` (AapsTopAppBar, AapsCard, etc.)

## Sizing Conventions
- Standard icon in icon button (toolbar/list): 24dp
- TonalIcon container: 40dp box, 24dp icon inside
- ConfigPluginItem (expanded plugin rows): 40dp tonal icon container, 24dp icon
- Category row leading icon: 24dp (no container)
- Material minimum touch target: 48dp (use `minimumInteractiveComponentSize` or ensure 48dp hit area)
- IconButton default size: 48dp touch area, but visually can be constrained with `.size()`

## Color Conventions
- All icon tints use `MaterialTheme.colorScheme.onSurfaceVariant` for secondary/decorative icons
- Primary/active plugin icons use `MaterialTheme.colorScheme.primary`
- Do NOT use hardcoded colors; use `MaterialTheme.colorScheme.*` or `AapsTheme.colors.*`

## ConfigurationScreen Patterns (ui module)
- Category rows: custom Row with clickable modifier, 24dp start padding, 16dp end padding
- Expanded plugin rows: ListItem with rounded card + secondaryContainer tinted background
- Chevron: `KeyboardArrowRight` (24dp), animated rotation 0→90° on expand
- Shortcut icon buttons use `.size(36dp)` constraint on IconButton with 20dp inner icon
- See: `ui/src/main/kotlin/app/aaps/ui/compose/configuration/ConfigurationScreen.kt`

## Accessibility Patterns
- Decorative icons that are part of a labeled row: `contentDescription = null` is acceptable
- Action icons (IconButton) MUST have meaningful contentDescription
- ConfigurationScreen Settings cog IconButton uses `contentDescription = null` — this is a known bug (line 321)
- Disabled alpha convention: `0.38f` (matches Material spec) — used inline as a local val, no shared constant in core/ui yet

## ConfigPluginItem Interaction Patterns
- Card is only clickable when plugin is enabled (line 337: `.then(if (isEnabled) Modifier.clickable {...} else Modifier)`)
- This creates an interaction gap: disabled plugins have no tap feedback at all — users may tap and think the app froze
- Switch `onCheckedChange = null` correctly disables it when `canToggle = false` (renders as non-interactive)
- The Settings cog is hidden when plugin is disabled (correct) — avoids dead-end navigation
- Trailing Row contains both cog + switch with no spacer between them — they sit very close together (touch target collision risk)
- `vertical = 2.dp` card margin is tight — cards stack with minimal breathing room

## Patterns Seen
- QuickLaunchConfigScreen uses `OutlinedIconButton` for add/remove row actions
- DrawerMenuItem uses full-width clickable rows with icon + label + description
- ListItem trailing content uses default IconButton (48dp touch target, no size constraint)
