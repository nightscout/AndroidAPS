# Project Preferences

## CRITICAL: Bash Command Rules (applies to ALL agents too)

- **NEVER use `cd && command` or `cd; command` in Bash calls** — triggers security approval prompts
  on Windows. Use absolute paths or `git -C` instead:
    - ✅ `git -C E:/GitHub/AndroidAPS diff HEAD -- path/to/file`
    - ✅ `git diff HEAD -- path/to/file` (CWD is already project root)
    - ❌ `cd E:/GitHub/AndroidAPS && git diff HEAD`
    - ❌ `cd /path; git status`
- **NEVER start a command with these — they are NOT in the allowlist and WILL trigger confirmation:
  **
    - ❌ `awk`, `cut`, `tr` → use `sed` or the Grep/Read tools instead
    - ❌ `sort`, `uniq` → wrap in `powershell.exe -Command "..."` or use tools
    - ❌ `diff` (standalone) → use `git diff` which IS allowed
    - ❌ `which` → use `where` instead (Windows equivalent, is allowed)
    - ❌ `chmod`, `chown` → not needed on Windows
    - ❌ `tar`, `gzip` → use `unzip` (allowed) or `powershell.exe -Command "..."`
    - ❌ `pip`, `npm`, `yarn` → use `python -m pip`, `node ...`, or `powershell.exe`
    - ❌ `gradlew.bat` without `./` prefix → always use `./gradlew.bat`
    - ❌ Starting a command with a file path (e.g., `E:/Github/.../gradlew.bat build`) → use
      `powershell.exe -Command "..."` wrapper instead
    - ❌ Compound commands with `&&`, `||`, or `;` as the top-level operator between separate
      commands → each command must start with an allowed prefix
- **Safe patterns that ARE allowed:** `git`, `gh`, `./gradlew.bat`, `powershell.exe`,
  `powershell`, `cmd`, `adb`, `curl`, `python`, `java`, `node`, `wsl`, `where`, `grep`, `find`,
  `echo`, `head`, `tail`, `sed`, `rm`, `del`, `ls`, `wc`, `tee`, `xargs`, `cat`, `mkdir`, `cp`,
  `mv`, `touch`, `unzip`, `jar`, `export`
- When spawning agents that use Bash, ALWAYS include this rule in the agent prompt
- See `.claude/CLAUDE_COMMANDS.md` for platform-specific working commands

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

- **NEVER make code changes without user confirmation** — When the user describes a problem or
  preference, propose the change first and wait for approval before editing code. Do NOT immediately
  edit files based on user feedback. The only exception is when the user explicitly says "do it",
  "fix it", "go ahead", or similar direct instruction.
- **Think critically, don't just agree** — Before implementing, evaluate whether the agreed approach
  is actually the best solution. Challenge assumptions, point out potential issues, suggest better
  alternatives. The user may miss something too. Ask "Is this the best way?" before writing code.
  Being a good collaborator means pushing back when you see a better path.
- **Implement EVERYTHING that was agreed upon** — Before writing code, review the plan/agreement
  and list all the steps. After user confirms, implement ALL of them completely. Do NOT implement
  half and skip the rest. If a feature was discussed and agreed (e.g., "unpair should clear keys +
  remove MAC + disconnect"), implement every part, not just one call. After implementing, mentally
  diff against the agreement to verify nothing was missed.
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
- On KSP error during compilation just compile again. Do not clean build.
- On file locked error stop gradle daemons (`gradlew.bat --stop`)
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

- **For migrations**: Follow procedures in `.claude/procedures/migration.md`

## When Stuck

- If compilation fails twice with the same error: **stop and show the error** to the user, don't
  keep retrying
- If a search finds nothing after 2 attempts: **ask the user** rather than guessing file locations
- If unsure about architecture or where code should go: **ask before implementing**
- If a tool call is denied: **ask why**, don't retry the same call
- If an approach requires more than 3 workarounds: **step back and reconsider the approach**
- If you realize you're about to repeat a mistake from memory: **stop and follow the correct pattern
  **

## Project Info

- Android project (AndroidAPS - open source artificial pancreas system)
- Main branch: `master`
- Development branch: `dev`
