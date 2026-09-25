---
name: feedback-mutation-restore-proof
description: Mutation testing must be ONE atomic bash call that restores unconditionally - never break in one step and restore in a later step
metadata:
  type: feedback
---

Break-and-restore must happen **inside a single Bash call**, so the file is back before the call
returns. Never break production code in one step and restore it in a later step.

**Why:** a run announced "Round 1: mutating X" and hit its turn cap immediately after applying the
mutation. It left live sabotage in the tree - `preferences.put(key, value.toString())` instead of
`cryptoUtil.hashPassword(...)`, i.e. the user's master password stored in plaintext - and no report
existed to say it was there. The coordinator found and reverted it. You can be stopped between any
two steps, so a two-step mutation is unsafe by construction.

**How to apply:** one call, `;`-separated so the restore runs whatever gradle returns:

```
S=<scratchpad>; f=<path/to/Target.kt>
cp "$f" "$S/mut-backup.kt"
sed -i 's/<original>/<mutation>/' "$f"
./gradlew.bat :<module>:testAndroidHostTest --no-daemon > "$S/mut.log" 2>&1
cp "$S/mut-backup.kt" "$f"
git -C E:/GitHub/AndroidAPS diff HEAD -- "$f"      # must print nothing
grep -E "BUILD SUCCESSFUL|BUILD FAILED" "$S/mut.log"
```

Restore with `cp` from the backup, not by reversing the `sed`. Then read the **test names** that went
red from the results XML - a failure *count* does not prove the right test caught it. Parse the XML
with a PowerShell `[xml]` cast over `testsuite.testcase` and print `$tc.failure` entries; the
regex-over-XML one-liner only gives totals.

Batch several mutations into one round when each breaks a distinct test and none masks another.
Never end a turn with production code modified, except by the real move itself. If turns are short,
restore first and report second.

Related: [[constraints-objectives-move]]
