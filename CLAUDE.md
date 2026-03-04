# Project Preferences

## CRITICAL: Bash Command Rules (applies to ALL agents too)

- **NEVER use `cd && command` or `cd; command` in Bash calls** — triggers security approval prompts
  on Windows. Use absolute paths or `git -C` instead:
    - ✅ `git -C E:/GitHub/AndroidAPS diff HEAD -- path/to/file`
    - ✅ `git diff HEAD -- path/to/file` (CWD is already project root)
    - ❌ `cd E:/GitHub/AndroidAPS && git diff HEAD`
    - ❌ `cd /path; git status`
- When spawning agents that use Bash, ALWAYS include this rule in the agent prompt

## Token Usage Reduction (Delay Conversation Compaction)

- **Use Task agents for exploration instead of direct Glob/Grep:**
    - `Task tool with subagent_type=Explore` summarizes findings
    - Direct Glob/Grep returns raw content which consumes more tokens
- **Limit file reads:**
    - Use `limit` parameter (100-200 lines) for large files
    - Use `offset` to target specific sections you need
    - Don't re-read files already seen in conversation
- **Use efficient Grep modes:**
    - `output_mode: "files_with_matches"` (default) - just file paths
    - `output_mode: "count"` - just counts
    - Only use `"content"` when you need actual line content
    - Use `head_limit` to cap number of results
- **Suppress verbose Bash output:**
    - Use `--quiet` flag for gradle: `.\gradlew.bat assembleFullDebug --quiet --no-daemon`
    - Pipe to `tail -50` for long outputs
    - Avoid commands that dump entire logs
- **Be specific in searches:**
    - Narrow glob patterns: `src/**/specific/*.kt` instead of `**/*.kt`
    - Precise regex patterns to reduce false matches
- **Avoid redundant operations:**
    - Reference line numbers from memory instead of re-reading
    - Don't explore same directories multiple times
    - Combine related searches when possible

## User Preferences

- **Do NOT mark anything as "fully functional" or "complete" without user confirmation:**
    - Always wait for user to test and confirm before claiming something works
    - Build success ≠ feature complete - user must verify runtime behavior
    - Never update PLAN.md or documentation to say something is "working" until user confirms
- **When user says "ALL":**
    - Do NOT optimize, batch, summarize, or take shortcuts
    - Create a todo item for EVERY item/file to be processed
    - Process each item individually and mark complete only after fully done
    - After each batch, state how many remain and continue until zero remain
    - Do NOT stop early or claim "done" until truly everything is processed
    - User WILL verify results - assume accountability
- **Always use explicit imports:**
    - Never use fully qualified names (e.g., `kotlin.math.abs`)
    - Always add proper import statements at the top of the file
    - Example: Add `import kotlin.math.abs` instead of using `kotlin.math.abs()`
- **Use centralized theme/styling:**
    - For Compose UI: Always use theme values instead of hardcoded dp/padding/colors. If proper
      setting doesn't exist, discuss it before creating hardcoded values.
    - If hardcoded values exist, consider moving them to theme for consistency and maintainability
- **NEVER use Android attrs in Compose code:**
    - ❌ BAD: `rh.gac(context, R.attr.highColor)` - Android attribute resolution doesn't work well
      with Compose
    - ✅ GOOD: Define colors in Compose theme and use them (e.g., `AapsTheme.colors.bgHigh`)
    - Domain models should NOT contain `@ColorInt` - use enums/sealed classes for classification
    - Map classification to theme colors in UI layer (Composables)
- **Type safety principles:**
    - Prefer specific types over `Any?` whenever possible
    - Document why `Any?` is used if it's truly necessary (e.g., module boundary constraints)
- **Systematic cleanup after migrations:**
    - Remove unused functions, parameters, and extensions
    - Delete deprecated code and escape hatches
    - Compile frequently to verify nothing breaks
- **Use TodoWrite for complex multi-step work:**
    - Create specific, actionable todo items (not vague descriptions)
    - Mark todos in_progress before starting, completed immediately after finishing
    - Keep exactly ONE todo in_progress at a time
    - Break down large tasks into smaller items for tracking
- **IMPORTANT: Read `.claude/CLAUDE_COMMANDS.md` at conversation start and after compaction** -
  Contains working commands for this local system
- **Update `.claude/CLAUDE_COMMANDS.md` continuously** - When discovering new working commands,
  useful patterns, or fixes for broken commands, add them to the file immediately
- See **CRITICAL: Bash Command Rules** section at top — never use `cd &&` or `cd;` patterns.
- On KSP error during compilation just compile again. Do not clean build.
- On file locked error stop gradle daemons
- Never install app automatically
- **NEVER run Android instrumented tests (connectedAndroidTest) without explicit user permission** —
  they uninstall the app from the device
- Use %TEMP% directory for screenshots
- Can edit files and run commands freely without asking for permission
- Can use internet/web search as needed
- For compilation do not use gradle daemon
- Do not decompile libraries, look for sources locally or ask first (especially Vico lib)
- **Skip compilation for trivial changes** — Don't run a build to verify simple edits like changing
  a number, string, color value, or dp size. Only compile when structural changes (new
  imports, API changes, type changes, new files) could cause errors.
- Avoid duplication while writing new code and resources. Prefer moving to another module. Elaborate
  if you think, it's necessary
- When resource strings are affected, change only english version. Ignore translations
- **Never manipulate localized strings programmatically** - Using patterns like
  `.replace(",", ".")`,
  `.removeSuffix(":")`, or stripping characters from resource strings breaks localization. Different
  languages have different punctuation and formatting rules. If a string needs different formats,
  create separate resource strings instead.
- **In Compose code, use `stringResource()` not `ResourceHelper`** - Compose has built-in
  `stringResource(R.string.xyz)` function. Only use `ResourceHelper` (rh) in non-Composable contexts
  (ViewModels, regular functions). This keeps Compose code cleaner and more idiomatic.
- **Clear focus on tap outside text fields** - Compose does NOT auto-clear focus like Android Views.
  For screens with text fields, use the shared `clearFocusOnTap` modifier:
  ```kotlin
  val focusManager = LocalFocusManager.current
  Column(modifier = Modifier.clearFocusOnTap(focusManager)) {
      // Content with text fields
  }
  ```
  The modifier is in `app.aaps.core.ui.compose.clearFocusOnTap`.
- **Avoid adding new inter-module (project) dependencies** - Adding
  `implementation(project(":other:module"))`
  between modules can significantly slow down compilation time. Always discuss before adding these.
  Prefer alternatives:
    - Inline constants instead of importing from another module
    - Move shared code to existing common modules
    - Use interfaces defined in core modules
    - Note: Adding external library dependencies via `api(libs.xxx)` or `implementation(libs.xxx)`
      is fine.

## Migration Procedures

When performing large-scale code migrations (e.g., XML→Compose, old API→new API):

### Planning Phase

- **Create a PLAN.md file** to track the entire migration
    - List all phases (Phase 0: Prerequisites, Phase 1-N: Implementation, Final: Cleanup)
    - Identify and categorize files by complexity (simple → medium → complex)
    - Track file counts and status for each phase
    - Update plan as you learn new information
    - During collection of information on every new info compare compatibilty with previous. Ask if
      it
      conflicts or something is not clear
- **Use grep/glob extensively** to find ALL instances before starting
    - Search for old patterns to ensure nothing is missed
    - Count total files/instances that need migration
    - Re-verify counts at the end

### Migration Strategy

- **Migrate in phases, simple to complex:**
    - Phase 0: Build prerequisites (base classes, shared code, utilities)
    - Phase 1: Simple cases with clear patterns
    - Phase 2: Medium complexity
    - Phase 3: Complex cases, special handling
    - Phase 4: Base classes that affect multiple files
    - Final Phase: Cleanup and remove old code
- **Identify base classes early:**
    - Changes to base classes affect many subclasses
    - Migrate base classes in dedicated phase
    - Update all subclasses together to avoid compilation errors
- **Maintain backward compatibility during migration:**
    - Keep both old and new code paths working
    - Mark old code as `@Deprecated` but don't remove yet
    - Only remove deprecated code when migration is 100% complete
    - Test that both paths work during transition

### Execution Phase

- **Migrate in batches, compile frequently:**
    - Don't batch too many changes before compiling
    - Compile after each phase or every 5-10 files
    - Fix compilation errors immediately
    - Use TodoWrite to track progress within each phase
- **Verify completeness rigorously:**
    - After claiming a phase is "done", search for old patterns
    - Count migrated files vs. initial count
    - User will verify - assume accountability
- **Document decisions in PLAN.md:**
    - Why certain approaches were chosen
    - What patterns emerged during migration
    - What issues were encountered and how solved
- **At the end of partial migration ask yourself if it's the best possible pattern independent to
  previous code. Discuss your findings.**

### Cleanup Phase (Only After 100% Migration Complete)

- **Systematic cleanup in specific order:**
    1. Remove `@Deprecated` markers and deprecated functions
    2. Delete unused extensions and helper functions
    3. Remove escape hatches and fallback code paths
    4. Delete unused parameters from interfaces/classes
    5. Replace generic types (`Any?`) with specific types
    6. Consolidate to single code path (remove if/else for old vs. new)
    7. Move hardcoded values to centralized theme/config
    8. Delete completely unused files
- **Verify after each cleanup step:**
    - Compile after each type of cleanup
    - Don't batch all cleanup before compiling
- **Update documentation:**
    - Remove TODOs referencing old code
    - Update comments to reflect new approach
    - Archive or delete PLAN.md if no longer needed

### Key Principles

- **Never claim "done" prematurely** - user will verify
- **Keep PLAN.md as single source of truth** for migration status
- **Compile frequently** - catch errors early
- **Cleanup only at the end** - don't mix migration with cleanup

## Project Info

- Android project (AndroidAPS - open source artificial pancreas system)
- Main branch: `master`
- Development branch: `dev`
