# Migration Procedures

When performing large-scale code migrations (e.g., XML→Compose, old API→new API):

## Planning Phase

- **Create a PLAN.md file** to track the entire migration
    - List all phases (Phase 0: Prerequisites, Phase 1-N: Implementation, Final: Cleanup)
    - Identify and categorize files by complexity (simple → medium → complex)
    - Track file counts and status for each phase
    - Update plan as you learn new information
    - During collection of information on every new info compare compatibility with previous. Ask if
      it conflicts or something is not clear
- **Use grep/glob extensively** to find ALL instances before starting
    - Search for old patterns to ensure nothing is missed
    - Count total files/instances that need migration
    - Re-verify counts at the end

## Migration Strategy

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

## Execution Phase

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

## Cleanup Phase (Only After 100% Migration Complete)

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

## Key Principles

- **Never claim "done" prematurely** - user will verify
- **Keep PLAN.md as single source of truth** for migration status
- **Compile frequently** - catch errors early
- **Cleanup only at the end** - don't mix migration with cleanup
