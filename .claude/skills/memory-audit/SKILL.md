---
name: memory-audit
description: Check stored project memories against the code, retire what is finished, and keep the index honest. Use when memories have not been reviewed for a long time, when a memory turns out to be wrong, when deciding what belongs in memory at all, or before trusting a memory to plan work.
---

# Auditing project memory

Memory goes stale quietly. A memory file is written once and then believed for months, because
nothing forces anyone to re-read it. The first full audit of a set that had been left alone for half
a year removed about 40% of it and had to correct more than half of what was left.

The goal is **not** that every machine holds the same memories. The goal is that each memory is
**true**. Syncing does not make memory true; auditing does.

## What belongs in memory at all

Decide this before you write, and re-decide it during an audit.

| Kind | Example | Where it goes |
|---|---|---|
| **Method and procedure** | how to flip a module, how to run this audit, a review checklist | a skill or a procedure in the repo - everybody benefits and git carries it |
| **Durable facts** | a stated preference, a decision made under pushback, a standing "do not re-add this", a trap in a tool | memory, and worth sharing between machines |
| **Working state** | file paths, counts, "migration is 60% done", "fix applied but not committed" | memory, but treat it as short-lived and local - it rots fastest |

**Anything re-derivable from the code is a liability, not an asset.** A memory that lists which files
a migration touched will be wrong within weeks, and someone will plan work from it. Prefer recording
*why* a decision was made, which the code cannot tell you.

Never put sensitive content in the public repo: analysis of unfixed safety bugs, machine names,
network addresses, key paths. Those stay in private memory.

## Procedure

**1. Back up, and verify the backup.** Copy the memory directory somewhere outside it. Check the copy
byte for byte (hash every file), not just that it has the same number of files. Retiring a memory is
not reversible without this.

**2. Check for a stale lock.** A crashed consolidation can leave a lock file behind holding the PID
of a process that no longer exists. While it is there, **every later run is silently refused** - which
is exactly how a set grows to hundreds of unreviewed files. Read the PID, confirm the process is
gone, then remove it. Removing the lock does not start anything; it only allows a run.

**3. Take stock.** List every file with size and date. Then find the two failure modes the index
hides:
- **orphans** - a file exists that the index does not link
- **broken links** - the index links a file that does not exist

**4. Audit against the code.** For a large set, fan out parallel agents batched by topic rather than
reading everything yourself. Give each agent the file list and ask it to: read the memory, pull out
every checkable claim (paths, symbol names, counts, "X is done"), verify each against the code **as
it is now**, and classify as VERIFIED / STALE (with what is true instead) / CANNOT TELL.

For **bug** memories the question is different and more important: **did the fix land since this was
written?** Have the agent read the blamed code as it stands and check `git log --grep` and
`git log -- <path>`. Expect a large share of "open" bugs to be closed already.

When spawning agents, repeat this repo's Bash rules in the prompt - agents do not follow CLAUDE.md
reliably. In particular: never `cd && command`, and do not start a command with `awk`, `sort`,
`uniq`, `which` or a bare file path.

**5. Fix and retire.** Split the work:
- **Mechanical** - moved paths, renamed symbols, corrected counts, a status that is factually wrong.
  Do these yourself. A dated correction block is enough; it is honest and much cheaper than a
  rewrite. Put it **below the frontmatter**, as the first paragraph of the body - never above the
  opening `---`. Frontmatter only parses as frontmatter when it is the first thing in the file, and a
  banner pushed above it turns `name` and `description` into ordinary text. The description is what
  recall matches on, so the memory you just corrected becomes the one nobody finds.
- **A correction that is itself a snapshot will need correcting again.** Replacing "zero components
  do X" with "these five do X today" fixes the fact and keeps the defect. If the claim is
  re-derivable, delete it and say how to re-derive it instead.
- **Retirement** - deleting a memory is a judgement call. Finished migrations go, keeping only the
  end state. Fixed bugs go unless they carry a standing "do not re-raise" decision or a still-open
  part. Ask before deleting in bulk.

A memory whose whole premise is dead (a framework that was replaced, a branch that was merged) is
worth rewriting rather than patching - keep the part that is still true and throw the rest away.

**6. Tidy the index.** One line per entry, each under about 150 characters, pointing at a file.
Shorten any line carrying detail that belongs inside the file.

## Traps that cost time

**An empty result is not an answer.** A search that returns nothing may mean "not there" or may mean
"looked in the wrong place". Always re-run with a control term you know must match. A tool that only
searches *other* sessions returns nothing when there are no other sessions, which looks identical to
a clean result.

**Content written directly into the index never gets reviewed.** It has no frontmatter, appears in no
file listing, and nobody opens it. One such block survived a whole dependency-injection migration
while pointing at a deleted file. **Every bullet in the index must be a link to a file.**

**Line numbers drift; symbol names mostly do not.** Cite a function or class, not `File.kt:123`.
Expect every line number older than a few months to be wrong.

**A source-set or framework move breaks paths wholesale.** After a large migration, nearly every path
in every memory is wrong at the directory level even when the file still exists. Check one, and you
know to check all. Do not assume one rule covers the repo either - some modules move and some do not,
so "every `src/main` is now `src/commonMain`" will itself be a false memory.

**A leftover worktree shadows the whole repo with a plausible past.** `git worktree list` can show a
checkout under `.claude/worktrees/` pinned months back and hidden from `git status` by
`.git/info/exclude`. It still sits inside the repo directory, so `find`, plain `grep` and globbing
walk into it and return pre-migration paths that look real - one such checkout held 635 files with
`import dagger` long after Dagger was removed. Run `git worktree list` before you trust a repo-wide
search, prefer `git -C <repo> grep` (which only sees the main worktree's tracked files), and treat a
hit under `.claude/worktrees/` as noise. Check the worktree for uncommitted work before suggesting
its removal, and leave the removal to the user.

**Watch for a feature that was dropped rather than migrated.** When a migration checklist lists items
you cannot find afterwards, they may have been lost rather than moved. Ask before assuming it was
deliberate - this has been a real user-facing regression before.

**A file's timestamp is not a change date.** `ls -l` shows when a checkout or pull last touched the
file on this machine, which can be days after the last real edit. Always ask git:
`git log -1 --date=short -- <path>`.

## A memory can rot without a single fact being wrong

There is a second kind of staleness that checking against the code will never find: **the memory
describes a practice that everybody quietly stopped following.** The file it names still exists, the
commits are there, every claim verifies - and the habit is dead.

Code cannot tell you this. Only behaviour can. For any memory that describes a way of working - a
hand-off file, a checklist to fill in, a doc to update, a naming convention - check the *last time
anyone actually did it*, and compare that against the work done since:

- `git log -1 --date=short -- <the file the practice maintains>` - when it was last really done
- `git log --since=<that date> --oneline` across the whole repo - what actually shipped since, and
  how much of it should have been recorded

Take the second half from the repo's history, **not from your own recollection of the session**. A
fresh session has no memory of what it did last week; it would see only "last commit on the 5th" and
have no way to tell whether anything since should have gone in. The gap is only visible when both
halves come from git.

A gap means one of three things, and only the user can say which:

- the practice lapsed and is worth reviving
- it served its purpose and the memory should go
- **the practice is alive and simply had nothing to record** - the work it tracks finished, or the
  thing it logs stopped happening

That third one is easy to miss and is often good news: an empty blockers list means there are no
blockers. Do not read silence as failure, and do not quietly "fix" the memory to match what is
happening now - surface the gap and ask.

## Finish by checking

- index and files match one to one
- no orphans, no broken links
- no bullet in the index is inline content
- **no new lock file appeared** - if one did, the run crashed, and the cause is still in the set

Then report what changed: how many files were retired, how many carried stale claims, and what is
still unverified.

**Keep this file up to date.** If an audit hits something not written here, add it before you finish.
Do not record findings from a particular audit here - those belong in memory. This file holds the
method only.
